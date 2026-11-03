package br.com.deladopara.shipping.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Deterministic package planning; it does not reserve stock or call a carrier. */
public final class PackageComposer {

    private static final List<Box> BOXES = List.of(
            new Box("P", 240, 180, 120, 250, 190, 130, 2_000, 150),
            new Box("M", 300, 260, 200, 310, 270, 210, 5_000, 250),
            new Box("G", 400, 320, 300, 410, 330, 310, 10_000, 400));

    private PackageComposer() {}

    public static List<PackagePlan> compose(List<Line> lines) {
        if (lines == null || lines.isEmpty() || lines.stream().anyMatch(Line::invalid)) {
            throw new IllegalArgumentException("shipping snapshot lines are invalid");
        }
        var units = new ArrayList<Line>();
        lines.stream()
                .sorted(Comparator.comparing(Line::category).thenComparing(Line::skuId))
                .forEach(line -> {
                    for (int quantity = 0; quantity < line.quantity(); quantity++) {
                        units.add(line.singleUnit());
                    }
                });

        var plans = new ArrayList<PackagePlan>();
        for (Line unit : units) {
            if (unit.fragile() || plans.stream().noneMatch(plan -> plan.category() == unit.category())) {
                plans.add(new PackagePlan(plans.size() + 1, unit.category(), unit.fragile(), unit));
                continue;
            }
            var target = plans.stream()
                    .filter(plan -> plan.category() == unit.category())
                    .filter(plan -> plan.canAdd(unit))
                    .findFirst();
            if (target.isPresent()) {
                target.get().add(unit);
            } else {
                plans.add(new PackagePlan(plans.size() + 1, unit.category(), false, unit));
            }
        }
        return List.copyOf(plans);
    }

    public record Line(
            UUID skuId,
            Category category,
            boolean fragile,
            int quantity,
            int lengthMm,
            int widthMm,
            int heightMm,
            int grossWeightGrams) {
        boolean invalid() {
            return skuId == null
                    || category == null
                    || quantity <= 0
                    || lengthMm <= 0
                    || widthMm <= 0
                    || heightMm <= 0
                    || grossWeightGrams <= 0;
        }

        Line singleUnit() {
            return new Line(skuId, category, fragile, 1, lengthMm, widthMm, heightMm, grossWeightGrams);
        }
    }

    public enum Category {
        FOOD,
        CRAFT
    }

    public static final class PackagePlan {
        private final int sequence;
        private final Category category;
        private final boolean fragile;
        private final List<Line> lines = new ArrayList<>();
        private int weightGrams;
        private int protectionWeightGrams;
        private Box box;

        private PackagePlan(int sequence, Category category, boolean fragile, Line first) {
            this.sequence = sequence;
            this.category = category;
            this.fragile = fragile;
            add(first);
        }

        private void add(Line line) {
            var oldWeight = weightGrams;
            var oldProtectionWeight = protectionWeightGrams;
            lines.add(line);
            weightGrams += line.grossWeightGrams();
            protectionWeightGrams += line.fragile() ? 100 : (lines.size() == 1 ? 50 : 0);
            box = smallestBox();
            if (box == null) {
                lines.remove(lines.size() - 1);
                weightGrams = oldWeight;
                protectionWeightGrams = oldProtectionWeight;
                throw new IllegalArgumentException("snapshot line exceeds available package sizes");
            }
        }

        private boolean canAdd(Line line) {
            if (line.fragile() || fragile) {
                return false;
            }
            var oldWeight = weightGrams;
            lines.add(line);
            weightGrams += line.grossWeightGrams();
            var fits = smallestBox() != null;
            lines.remove(lines.size() - 1);
            weightGrams = oldWeight;
            return fits;
        }

        private Box smallestBox() {
            return BOXES.stream()
                    .filter(candidate ->
                            weightGrams + protectionWeightGrams + candidate.tareGrams() <= candidate.maxWeightGrams())
                    .filter(candidate -> fits(lines, candidate))
                    .findFirst()
                    .orElse(null);
        }

        private static boolean fits(List<Line> lines, Box box) {
            if (lines.size() > 64) {
                return false;
            }
            var items = lines.stream()
                    .map(line -> new PackItem(
                            line.skuId(),
                            line.fragile() ? line.lengthMm() + 60 : line.lengthMm() + 20,
                            line.fragile() ? line.widthMm() + 60 : line.widthMm() + 20,
                            line.fragile() ? line.heightMm() + 60 : line.heightMm() + 20))
                    .sorted(Comparator.comparingLong(PackItem::volume)
                            .reversed()
                            .thenComparing(item -> item.skuId().toString()))
                    .toList();
            return place(
                    0,
                    items,
                    List.of(new Position(0, 0, 0)),
                    new ArrayList<>(),
                    new int[] {20_000},
                    box.innerLengthMm(),
                    box.innerWidthMm(),
                    box.innerHeightMm());
        }

        private static boolean place(
                int itemIndex,
                List<PackItem> items,
                List<Position> positions,
                List<PlacedItem> placed,
                int[] searchBudget,
                int maxLength,
                int maxWidth,
                int maxHeight) {
            if (itemIndex == items.size()) {
                return true;
            }
            var item = items.get(itemIndex);
            var orderedPositions = positions.stream()
                    .distinct()
                    .sorted(Comparator.comparingInt(Position::z)
                            .thenComparingInt(Position::y)
                            .thenComparingInt(Position::x))
                    .toList();
            for (var position : orderedPositions) {
                for (var rotation : rotations(item)) {
                    if (--searchBudget[0] < 0) {
                        return false;
                    }
                    if (position.x() + rotation.length() > maxLength
                            || position.y() + rotation.width() > maxWidth
                            || position.z() + rotation.height() > maxHeight) {
                        continue;
                    }
                    var candidate = new PlacedItem(position, rotation);
                    if (placed.stream().anyMatch(existing -> overlaps(candidate, existing))) {
                        continue;
                    }
                    placed.add(candidate);
                    var nextPositions = new ArrayList<>(positions);
                    nextPositions.add(new Position(position.x() + rotation.length(), position.y(), position.z()));
                    nextPositions.add(new Position(position.x(), position.y() + rotation.width(), position.z()));
                    nextPositions.add(new Position(position.x(), position.y(), position.z() + rotation.height()));
                    if (place(
                            itemIndex + 1,
                            items,
                            nextPositions,
                            placed,
                            searchBudget,
                            maxLength,
                            maxWidth,
                            maxHeight)) {
                        return true;
                    }
                    placed.remove(placed.size() - 1);
                }
            }
            return false;
        }

        private static List<Dimensions> rotations(PackItem item) {
            var candidates = List.of(
                    new Dimensions(item.lengthMm(), item.widthMm(), item.heightMm()),
                    new Dimensions(item.lengthMm(), item.heightMm(), item.widthMm()),
                    new Dimensions(item.widthMm(), item.lengthMm(), item.heightMm()),
                    new Dimensions(item.widthMm(), item.heightMm(), item.lengthMm()),
                    new Dimensions(item.heightMm(), item.lengthMm(), item.widthMm()),
                    new Dimensions(item.heightMm(), item.widthMm(), item.lengthMm()));
            return candidates.stream().distinct().toList();
        }

        private static boolean overlaps(PlacedItem first, PlacedItem second) {
            return first.position().x()
                            < second.position().x() + second.dimensions().length()
                    && first.position().x() + first.dimensions().length()
                            > second.position().x()
                    && first.position().y()
                            < second.position().y() + second.dimensions().width()
                    && first.position().y() + first.dimensions().width()
                            > second.position().y()
                    && first.position().z()
                            < second.position().z() + second.dimensions().height()
                    && first.position().z() + first.dimensions().height()
                            > second.position().z();
        }

        public int sequence() {
            return sequence;
        }

        public Category category() {
            return category;
        }

        public boolean fragile() {
            return fragile;
        }

        public List<Line> lines() {
            return List.copyOf(lines);
        }

        public String boxCode() {
            return box.code();
        }

        public int totalWeightGrams() {
            return weightGrams + protectionWeightGrams + box.tareGrams();
        }

        public int lengthMm() {
            return box.outerLengthMm();
        }

        public int widthMm() {
            return box.outerWidthMm();
        }

        public int heightMm() {
            return box.outerHeightMm();
        }
    }

    private record Box(
            String code,
            int innerLengthMm,
            int innerWidthMm,
            int innerHeightMm,
            int outerLengthMm,
            int outerWidthMm,
            int outerHeightMm,
            int maxWeightGrams,
            int tareGrams) {}

    private record PackItem(UUID skuId, int lengthMm, int widthMm, int heightMm) {
        long volume() {
            return (long) lengthMm * widthMm * heightMm;
        }
    }

    private record Dimensions(int length, int width, int height) {}

    private record Position(int x, int y, int z) {}

    private record PlacedItem(Position position, Dimensions dimensions) {}
}
