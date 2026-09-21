package br.com.deladopara.inventory.adapter.persistence;

import br.com.deladopara.inventory.adapter.web.dto.InventoryAdjustmentRequest;
import br.com.deladopara.inventory.adapter.web.dto.InventoryLotResponse;
import jakarta.persistence.OptimisticLockException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAdjustmentService {

    private final InventoryLotRepository lots;
    private final InventoryMovementRepository movements;

    public InventoryAdjustmentService(InventoryLotRepository lots, InventoryMovementRepository movements) {
        this.lots = lots;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<InventoryLotResponse> list(UUID skuId) {
        return lots.findAllBySkuIdOrderByReceivedAtAscIdAsc(skuId).stream()
                .map(InventoryAdjustmentService::response)
                .toList();
    }

    @Transactional
    public InventoryLotResponse adjust(UUID lotId, InventoryAdjustmentRequest request) {
        var lot = lots.findById(lotId).orElseThrow(() -> new IllegalArgumentException("Inventory lot not found"));
        if (lot.getVersion() != request.expectedVersion()) {
            throw new OptimisticLockException("Inventory lot version conflict");
        }
        var delta = request.physicalUnits() - lot.getPhysicalUnits();
        lot.adjustPhysicalUnits(request.physicalUnits(), request.actorId(), request.reason());
        var saved = lots.save(lot);
        if (delta != 0) {
            movements.save(new InventoryMovementEntity(
                    UUID.randomUUID(),
                    saved.getSku(),
                    "ADMIN_ADJUSTMENT",
                    delta,
                    "lot-adjustment:" + saved.getId() + ":" + (saved.getVersion() + 1),
                    request.actorId(),
                    request.reason(),
                    java.time.Instant.now()));
        }
        return response(saved);
    }

    private static InventoryLotResponse response(InventoryLotEntity lot) {
        return new InventoryLotResponse(
                lot.getId(),
                lot.getSku().getSkuCode(),
                lot.getPhysicalUnits(),
                lot.getReservedUnits(),
                lot.getPhysicalUnits() - lot.getReservedUnits(),
                lot.isBlocked(),
                lot.getExpiresOn(),
                lot.getMinimumShelfLifeDays(),
                lot.getVersion());
    }
}
