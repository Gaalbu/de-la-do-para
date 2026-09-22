package br.com.deladopara.inventory.adapter.persistence;

import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.inventory.adapter.web.dto.InventoryAdjustmentRequest;
import br.com.deladopara.inventory.adapter.web.dto.InventoryLotCreateRequest;
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
    private final ProductSkuRepository skus;

    public InventoryAdjustmentService(
            InventoryLotRepository lots, InventoryMovementRepository movements, ProductSkuRepository skus) {
        this.lots = lots;
        this.movements = movements;
        this.skus = skus;
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

    @Transactional
    public InventoryLotResponse receive(UUID skuId, InventoryLotCreateRequest request) {
        var sku = skus.findById(skuId).orElseThrow(() -> new IllegalArgumentException("SKU not found"));
        var now = java.time.Instant.now();
        var lot = new InventoryLotEntity(
                UUID.randomUUID(),
                sku,
                request.physicalUnits(),
                0,
                false,
                request.expiresOn(),
                request.minimumShelfLifeDays(),
                request.receivedAt(),
                now);
        var saved = lots.save(lot);
        movements.save(new InventoryMovementEntity(
                UUID.randomUUID(),
                sku,
                "RECEIPT",
                request.physicalUnits(),
                "lot-receipt:" + saved.getId(),
                null,
                "recebimento inicial",
                now));
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
