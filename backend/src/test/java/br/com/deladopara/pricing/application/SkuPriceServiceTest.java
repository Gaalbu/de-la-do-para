package br.com.deladopara.pricing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.pricing.adapter.persistence.SkuPriceEntity;
import br.com.deladopara.pricing.adapter.persistence.SkuPriceRepository;
import br.com.deladopara.pricing.domain.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkuPriceServiceTest {

    @Test
    void loadsCurrentPricesInOneRepositoryCall() {
        var skuId = UUID.randomUUID();
        var repository = mock(SkuPriceRepository.class);
        when(repository.findAllBySkuIdIn(List.of(skuId)))
                .thenReturn(List.of(new SkuPriceEntity(skuId, Money.brl(1_800), Instant.EPOCH)));

        var result = new SkuPriceService(repository).findCurrentPrices(List.of(skuId));

        assertThat(result).containsEntry(skuId, new br.com.deladopara.pricing.domain.SkuPrice(skuId, Money.brl(1_800)));
    }
}
