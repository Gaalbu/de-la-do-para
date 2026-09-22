package br.com.deladopara.inventory.adapter.persistence;

import br.com.deladopara.catalog.domain.ProductSku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovementEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false, updatable = false)
    private ProductSku sku;

    @Column(name = "movement_type", nullable = false, updatable = false, length = 32)
    private String movementType;

    @Column(name = "units_delta", nullable = false, updatable = false)
    private int unitsDelta;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected InventoryMovementEntity() {}

    public InventoryMovementEntity(
            UUID id,
            ProductSku sku,
            String movementType,
            int unitsDelta,
            String idempotencyKey,
            UUID actorId,
            String reason,
            Instant occurredAt) {
        if (id == null
                || sku == null
                || movementType == null
                || movementType.isBlank()
                || unitsDelta == 0
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || reason == null
                || reason.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("Movement fields are invalid");
        }
        this.id = id;
        this.sku = sku;
        this.movementType = movementType;
        this.unitsDelta = unitsDelta;
        this.idempotencyKey = idempotencyKey;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public ProductSku getSku() {
        return sku;
    }

    public String getMovementType() {
        return movementType;
    }

    public int getUnitsDelta() {
        return unitsDelta;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
