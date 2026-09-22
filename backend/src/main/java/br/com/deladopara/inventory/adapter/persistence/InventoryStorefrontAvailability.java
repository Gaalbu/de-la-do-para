package br.com.deladopara.inventory.adapter.persistence;

import br.com.deladopara.catalog.application.StorefrontAvailability;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class InventoryStorefrontAvailability implements StorefrontAvailability {

    private final InventoryLotRepository lots;

    public InventoryStorefrontAvailability(InventoryLotRepository lots) {
        this.lots = lots;
    }

    @Override
    public Map<UUID, Integer> freeUnits(List<UUID> skuIds, LocalDate availableOn) {
        return lots.findAllBySkuIdInOrderBySkuIdAscReceivedAtAscIdAsc(skuIds).stream()
                .filter(lot -> !lot.isBlocked()
                        && (lot.getExpiresOn() == null || !lot.getExpiresOn().isBefore(availableOn)))
                .collect(Collectors.groupingBy(
                        lot -> lot.getSku().getId(),
                        Collectors.summingInt(lot -> lot.getPhysicalUnits() - lot.getReservedUnits())));
    }
}
