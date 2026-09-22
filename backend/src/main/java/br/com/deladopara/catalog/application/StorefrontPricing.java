package br.com.deladopara.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface StorefrontPricing {

    Map<UUID, Long> currentPriceCents(List<UUID> skuIds);
}
