package br.com.deladopara.shipping.adapter.persistence;

import br.com.deladopara.shipping.application.ShippingQuote;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shipping_quotes")
public class ShippingQuoteEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID snapshotId;

    @Column(nullable = false)
    private long snapshotVersion;

    @Column(nullable = false, length = 8)
    private String destinationPostalCode;

    @Column(nullable = false, length = 128)
    private String inputFingerprint;

    @Column(nullable = false, length = 80)
    private String serviceId;

    @Column(nullable = false, length = 160)
    private String serviceName;

    @Column(nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int deliveryDays;

    @Column(nullable = false)
    private int preparationDays;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String packageSequences;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected ShippingQuoteEntity() {}

    public ShippingQuoteEntity(ShippingQuote quote, String packageSequences) {
        this.id = quote.id();
        this.snapshotId = quote.snapshotId();
        this.snapshotVersion = quote.snapshotVersion();
        this.destinationPostalCode = quote.destinationPostalCode();
        this.inputFingerprint = quote.inputFingerprint();
        this.serviceId = quote.serviceId();
        this.serviceName = quote.serviceName();
        this.priceCents = quote.priceCents();
        this.deliveryDays = quote.deliveryDays();
        this.preparationDays = quote.preparationDays();
        this.packageSequences = packageSequences;
        this.createdAt = quote.createdAt();
        this.expiresAt = quote.expiresAt();
    }

    public ShippingQuote toDomain(List<Integer> sequences) {
        return new ShippingQuote(
                id,
                snapshotId,
                snapshotVersion,
                destinationPostalCode,
                inputFingerprint,
                serviceId,
                serviceName,
                priceCents,
                deliveryDays,
                preparationDays,
                sequences,
                createdAt,
                expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public String getPackageSequences() {
        return packageSequences;
    }
}
