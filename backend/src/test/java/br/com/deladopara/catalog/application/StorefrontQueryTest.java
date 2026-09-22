package br.com.deladopara.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StorefrontQueryTest {

    @Test
    void normalizesProducerAndAppliesSafeDefaults() {
        var query = new StorefrontQuery("  Demo-Producer ", null, null, null, null, 0, 20);

        assertThat(query.producerSlug()).isEqualTo("demo-producer");
        assertThat(query.sort()).isEqualTo(StorefrontQuery.Sort.RELEVANCE);
    }

    @Test
    void rejectsInvertedPriceRange() {
        assertThatThrownBy(() -> new StorefrontQuery(null, null, 2_000L, 1_000L, null, 0, 20))
                .isInstanceOf(ProductService.InvalidProductInputException.class)
                .hasMessage("faixa de preço inválida");
    }

    @Test
    void rejectsUnboundedPageSize() {
        assertThatThrownBy(() -> new StorefrontQuery(null, null, null, null, null, 0, 51))
                .isInstanceOf(ProductService.InvalidProductInputException.class);
    }
}
