package br.com.deladopara.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PickupOptionsServiceTest {

    @Test
    void returnsUnavailableWhenSnapshotContainsSkuWithoutPickupEligibility() {
        var skus = mock(ProductSkuRepository.class);
        var skuId = UUID.randomUUID();
        when(skus.findById(skuId)).thenReturn(java.util.Optional.empty());
        var service = new PickupOptionsService(skus, new ObjectMapper());

        var result = service.evaluate("[{\"skuId\":\"" + skuId + "\",\"quantity\":1}]");

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.unavailableSkuIds()).containsExactly(skuId);
    }
}
