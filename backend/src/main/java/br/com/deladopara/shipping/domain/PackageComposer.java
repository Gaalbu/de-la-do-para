package br.com.deladopara.shipping.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Deterministic package planning; it does not reserve stock or call a carrier. */
public final class PackageComposer {

    private static final List<Box> BOXES = List.of(
            new Box("P", 240, 180, 120, 2_000, 150),
            new Box("M", 300, 260, 200, 5_000, 250),
            new Box("G", 400, 320, 300, 10_000, 400));

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
        private int lengthMm;
        private int widthMm;
        private int heightMm;
        private Box box;

        private PackagePlan(int sequence, Category category, boolean fragile, Line first) {
            this.sequence = sequence;
            this.category = category;
            this.fragile = fragile;
            add(first);
        }

        private void add(Line line) {
            lines.add(line);
            weightGrams += line.grossWeightGrams();
            lengthMm = Math.max(lengthMm, protectedLength(line));
            widthMm = Math.max(widthMm, protectedWidth(line));
            heightMm = Math.max(heightMm, protectedHeight(line));
            box = smallestBox();
            if (box == null) {
                lines.remove(lines.size() - 1);
                weightGrams -= line.grossWeightGrams();
                throw new IllegalArgumentException("snapshot line exceeds available package sizes");
            }
        }

        private boolean canAdd(Line line) {
            if (line.fragile() || fragile) return false;
            var oldWeight = weightGrams;
            var oldLength = lengthMm;
            var oldWidth = widthMm;
            var oldHeight = heightMm;
            weightGrams += line.grossWeightGrams();
            lengthMm = Math.max(lengthMm, protectedLength(line));
            widthMm = Math.max(widthMm, protectedWidth(line));
            heightMm = Math.max(heightMm, protectedHeight(line));
            var fits = smallestBox() != null;
            weightGrams = oldWeight;
            lengthMm = oldLength;
            widthMm = oldWidth;
            heightMm = oldHeight;
            return fits;
        }

        private Box smallestBox() {
            return BOXES.stream()
                    .filter(candidate -> weightGrams + candidate.tareGrams() <= candidate.maxWeightGrams())
                    .filter(candidate -> lengthMm <= candidate.lengthMm()
                            && widthMm <= candidate.widthMm()
                            && heightMm <= candidate.heightMm())
                    .findFirst()
                    .orElse(null);
        }

        private int protectedLength(Line line) {
            return line.fragile() ? line.lengthMm() + 60 : line.lengthMm() + 10;
        }

        private int protectedWidth(Line line) {
            return line.fragile() ? line.widthMm() + 60 : line.widthMm() + 10;
        }

        private int protectedHeight(Line line) {
            return line.fragile() ? line.heightMm() + 60 : line.heightMm() + 10;
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
            return weightGrams + box.tareGrams();
        }

        public int lengthMm() {
            return lengthMm;
        }

        public int widthMm() {
            return widthMm;
        }

        public int heightMm() {
            return heightMm;
        }
    }

    private record Box(String code, int lengthMm, int widthMm, int heightMm, int maxWeightGrams, int tareGrams) {}
}
