package br.com.deladopara.pricing.application;

import br.com.deladopara.pricing.adapter.persistence.SkuPriceRepository;
import br.com.deladopara.pricing.domain.SkuPrice;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkuPriceService {

    private final SkuPriceRepository prices;

    public SkuPriceService(SkuPriceRepository prices) {
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public Map<UUID, SkuPrice> findCurrentPrices(List<UUID> skuIds) {
        if (skuIds == null || skuIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("SKU identities are required");
        }
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        return prices.findAllBySkuIdIn(skuIds).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toUnmodifiableMap(SkuPrice::skuId, Function.identity()));
    }
}
