package br.com.deladopara.inventory.adapter.persistence;

import br.com.deladopara.catalog.domain.ProductSku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_lots")
public class InventoryLotEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sku_id", nullable = false, updatable = false)
    private ProductSku sku;

    @Column(name = "physical_units", nullable = false)
    private int physicalUnits;

    @Column(name = "reserved_units", nullable = false)
    private int reservedUnits;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "minimum_shelf_life_days")
    private Integer minimumShelfLifeDays;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected InventoryLotEntity() {}

    public InventoryLotEntity(
            UUID id,
            ProductSku sku,
            int physicalUnits,
            int reservedUnits,
            boolean blocked,
            LocalDate expiresOn,
            Integer minimumShelfLifeDays,
            Instant receivedAt,
            Instant now) {
        if (id == null || sku == null || receivedAt == null || now == null) {
            throw new IllegalArgumentException("Lot identity, SKU and timestamps are required");
        }
        if (physicalUnits < 0 || reservedUnits < 0 || reservedUnits > physicalUnits) {
            throw new IllegalArgumentException("Lot balance is invalid");
        }
        if ((expiresOn == null) != (minimumShelfLifeDays == null)
                || (minimumShelfLifeDays != null && minimumShelfLifeDays < 0)) {
            throw new IllegalArgumentException("Lot validity fields must be paired");
        }
        this.id = id;
        this.sku = sku;
        this.physicalUnits = physicalUnits;
        this.reservedUnits = reservedUnits;
        this.blocked = blocked;
        this.expiresOn = expiresOn;
        this.minimumShelfLifeDays = minimumShelfLifeDays;
        this.receivedAt = receivedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public ProductSku getSku() {
        return sku;
    }

    public int getPhysicalUnits() {
        return physicalUnits;
    }

    public int getReservedUnits() {
        return reservedUnits;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public Integer getMinimumShelfLifeDays() {
        return minimumShelfLifeDays;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void adjustPhysicalUnits(int newPhysicalUnits, UUID actorId, String reason) {
        if (newPhysicalUnits < reservedUnits) {
            throw new IllegalArgumentException("Adjustment cannot reduce below reserved units");
        }
        if (actorId == null || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Adjustment actor and reason are required");
        }
        physicalUnits = newPhysicalUnits;
        updatedAt = Instant.now();
    }
}
