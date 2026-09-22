package br.com.deladopara.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkuPriceTest {

    @Test
    void acceptsAPositiveCurrentBrlPrice() {
        var skuId = UUID.randomUUID();

        var price = new SkuPrice(skuId, Money.brl(1_800));

        assertThat(price.skuId()).isEqualTo(skuId);
        assertThat(price.unitPrice()).isEqualTo(Money.brl(1_800));
    }

    @Test
    void rejectsMissingSku() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SkuPrice(null, Money.brl(1_800)))
                .withMessage("SKU identity is required");
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SkuPrice(UUID.randomUUID(), Money.brl(0)))
                .withMessage("SKU price must be positive");
    }
}
