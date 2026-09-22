package br.com.deladopara.catalog.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface StorefrontAvailability {

    Map<UUID, Integer> freeUnits(List<UUID> skuIds, LocalDate availableOn);
}
