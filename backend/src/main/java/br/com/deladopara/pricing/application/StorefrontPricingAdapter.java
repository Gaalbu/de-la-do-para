package br.com.deladopara.pricing.application;

import br.com.deladopara.catalog.application.StorefrontPricing;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StorefrontPricingAdapter implements StorefrontPricing {

    private final SkuPriceService prices;

    public StorefrontPricingAdapter(SkuPriceService prices) {
        this.prices = prices;
    }

    @Override
    public Map<UUID, Long> currentPriceCents(List<UUID> skuIds) {
        return prices.findCurrentPrices(skuIds).values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        price -> price.skuId(), price -> price.unitPrice().cents()));
    }
}
