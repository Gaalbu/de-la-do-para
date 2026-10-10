package br.com.deladopara.shipping.adapter.persistence;

import br.com.deladopara.shipping.domain.ShippingLabelOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipping_label_operation")
public class ShippingLabelOperationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID shipmentUnitId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<Integer> packageSequences;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ShippingLabelOperation.Step step;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShippingLabelOperation.State state;

    @Column(nullable = false)
    private UUID correlationId;

    @Column(length = 160)
    private String providerShipmentId;

    @Column(length = 80)
    private String failureCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant requestedAt;

    private Instant resolvedAt;

    @Version
    private long version;

    protected ShippingLabelOperationEntity() {}

    public ShippingLabelOperationEntity(ShippingLabelOperation operation) {
        this.id = operation.id();
        this.orderId = operation.orderId();
        this.shipmentUnitId = operation.shipmentUnitId();
        this.packageSequences = operation.packageSequences();
        this.step = operation.step();
        this.state = operation.state();
        this.correlationId = operation.correlationId();
        this.providerShipmentId = operation.providerShipmentId();
        this.failureCode = operation.failureCode();
        this.createdAt = operation.createdAt();
        this.updatedAt = operation.updatedAt();
        this.requestedAt = operation.requestedAt();
        this.resolvedAt = operation.resolvedAt();
    }

    public ShippingLabelOperation toDomain() {
        return new ShippingLabelOperation(
                id,
                orderId,
                shipmentUnitId,
                packageSequences,
                step,
                state,
                correlationId,
                providerShipmentId,
                failureCode,
                createdAt,
                updatedAt,
                requestedAt,
                resolvedAt);
    }

    public void apply(ShippingLabelOperation operation) {
        if (!id.equals(operation.id())
                || !orderId.equals(operation.orderId())
                || !shipmentUnitId.equals(operation.shipmentUnitId())
                || !packageSequences.equals(operation.packageSequences())
                || step != operation.step()
                || !correlationId.equals(operation.correlationId())
                || !createdAt.equals(operation.createdAt())) {
            throw new IllegalArgumentException("shipping operation identity cannot change");
        }
        state = operation.state();
        providerShipmentId = operation.providerShipmentId();
        failureCode = operation.failureCode();
        updatedAt = operation.updatedAt();
        requestedAt = operation.requestedAt();
        resolvedAt = operation.resolvedAt();
    }
}
