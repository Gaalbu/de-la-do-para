package br.com.deladopara.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackageComposerTest {

    private static final UUID FOOD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CRAFT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void separatesCategoriesAndPutsEachFragilePieceInItsOwnPackage() {
        var plans = PackageComposer.compose(List.of(
                new PackageComposer.Line(CRAFT, PackageComposer.Category.CRAFT, true, 2, 100, 100, 100, 100),
                new PackageComposer.Line(FOOD, PackageComposer.Category.FOOD, false, 2, 100, 100, 100, 100)));

        assertThat(plans).hasSize(3);
        assertThat(plans.get(0).category()).isEqualTo(PackageComposer.Category.FOOD);
        assertThat(plans.get(0).lines()).hasSize(2);
        assertThat(plans.get(1).fragile()).isTrue();
        assertThat(plans.get(2).fragile()).isTrue();
        assertThat(plans.stream().flatMap(plan -> plan.lines().stream()).map(PackageComposer.Line::quantity))
                .allMatch(quantity -> quantity == 1 || quantity == 2);
    }

    @Test
    void usesSmallestFittingBoxAndFailsWhenAUnitCannotFit() {
        var small = PackageComposer.compose(
                List.of(new PackageComposer.Line(FOOD, PackageComposer.Category.FOOD, false, 1, 100, 100, 100, 100)));
        assertThat(small.getFirst().boxCode()).isEqualTo("P");

        assertThatThrownBy(() -> PackageComposer.compose(List.of(
                        new PackageComposer.Line(FOOD, PackageComposer.Category.FOOD, false, 1, 500, 100, 100, 100))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
