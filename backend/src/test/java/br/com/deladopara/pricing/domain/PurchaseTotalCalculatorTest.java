package br.com.deladopara.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseTotalCalculatorTest {

    private final PurchaseTotalCalculator calculator = new PurchaseTotalCalculator();

    @Test
    void calculatesSubtotalFreightDiscountAndTotalInCents() {
        var result = calculator.calculate(new PurchaseTotalRequest(
                List.of(new PurchaseLine("FARINHA", 1800, 1)), 750, CouponDiscount.percentage(10)));

        assertThat(result.subtotalCents()).isEqualTo(1800);
        assertThat(result.discountCents()).isEqualTo(180);
        assertThat(result.shippingCents()).isEqualTo(750);
        assertThat(result.totalCents()).isEqualTo(2370);
    }

    @Test
    void roundsPercentageHalfUpAndCapsFixedDiscountAtSubtotal() {
        var percentage = calculator.calculate(
                new PurchaseTotalRequest(List.of(new PurchaseLine("ITEM", 1801, 1)), 0, CouponDiscount.percentage(15)));
        var fixed = calculator.calculate(
                new PurchaseTotalRequest(List.of(new PurchaseLine("ITEM", 1800, 1)), 0, CouponDiscount.fixed(2500)));

        assertThat(percentage.discountCents()).isEqualTo(270);
        assertThat(percentage.totalCents()).isEqualTo(1531);
        assertThat(fixed.discountCents()).isEqualTo(1800);
        assertThat(fixed.totalCents()).isZero();
    }

    @Test
    void rejectsInvalidMoneyAndQuantities() {
        assertThatThrownBy(() -> calculator.calculate(
                        new PurchaseTotalRequest(List.of(new PurchaseLine("ITEM", -1, 1)), 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(
                        new PurchaseTotalRequest(List.of(new PurchaseLine("ITEM", 100, 0)), 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(
                        new PurchaseTotalRequest(List.of(new PurchaseLine("ITEM", 100, 1)), -1, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsForeignCurrencyAndUnmetCouponMinimum() {
        assertThatThrownBy(() -> calculator.calculate(new PurchaseTotalRequest(
                        List.of(new PurchaseLine("ITEM", new Money(Currency.USD, 100), 1)), 0, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(new PurchaseTotalRequest(
                        List.of(new PurchaseLine("ITEM", 1800, 1)), 0, CouponDiscount.percentage(10, 2000))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
