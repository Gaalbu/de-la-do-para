package br.com.deladopara.shipping.application;

import br.com.deladopara.orders.application.OrderFulfillmentPort;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.shipping.adapter.persistence.ShippingLabelOperationEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingLabelOperationRepository;
import br.com.deladopara.shipping.domain.ShippingLabelOperation;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingLabelOperationService {

    private final ShippingLabelOperationRepository operations;
    private final OrderFulfillmentPort orders;

    public ShippingLabelOperationService(ShippingLabelOperationRepository operations, OrderFulfillmentPort orders) {
        this.operations = operations;
        this.orders = orders;
    }

    @Transactional
    public ShippingLabelOperation create(ShippingLabelOperation operation) {
        var order = orders.lock(operation.orderId());
        var existing = operations.findByOrderIdAndShipmentUnitIdAndStep(
                operation.orderId(), operation.shipmentUnitId(), operation.step());
        if (existing.isPresent()) {
            var persisted = existing.get().toDomain();
            if (!persisted.packageSequences().equals(operation.packageSequences())
                    || (operation.step() != ShippingLabelOperation.Step.ADD_TO_CART
                            && !persisted.providerShipmentId().equals(operation.providerShipmentId()))) {
                throw new IllegalArgumentException("shipping operation does not match the persisted package mapping");
            }
            return persisted;
        }
        if (order.mode() != FulfillmentMode.DELIVERY
                || (order.status() != OrderStatus.PAID && order.status() != OrderStatus.PREPARING)) {
            throw new IllegalStateException("shipping labels require an eligible delivery order");
        }
        requireSuccessfulPriorStep(operation);
        return operations
                .saveAndFlush(new ShippingLabelOperationEntity(operation))
                .toDomain();
    }

    @Transactional(readOnly = true)
    public ShippingLabelOperation find(UUID operationId) {
        return operations
                .findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("shipping operation was not found"))
                .toDomain();
    }

    @Transactional
    public ShippingLabelOperation request(UUID operationId, Instant now) {
        return transition(operationId, current -> current.request(now));
    }

    @Transactional
    public ShippingLabelOperation succeed(UUID operationId, String providerShipmentId, Instant now) {
        return transition(operationId, current -> current.succeed(providerShipmentId, now));
    }

    @Transactional
    public ShippingLabelOperation fail(UUID operationId, String failureCode, Instant now) {
        return transition(operationId, current -> current.fail(failureCode, now));
    }

    @Transactional
    public ShippingLabelOperation markUnknown(UUID operationId, Instant now) {
        return transition(operationId, current -> current.markUnknown(now));
    }

    @Transactional
    public ShippingLabelOperation reconcileSucceeded(UUID operationId, String providerShipmentId, Instant now) {
        return transition(operationId, current -> current.reconcileSucceeded(providerShipmentId, now));
    }

    @Transactional
    public ShippingLabelOperation reconcileNotApplied(UUID operationId, String failureCode, Instant now) {
        return transition(operationId, current -> current.reconcileNotApplied(failureCode, now));
    }

    private ShippingLabelOperation transition(
            UUID operationId, java.util.function.UnaryOperator<ShippingLabelOperation> update) {
        var entity = operations
                .findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("shipping operation was not found"));
        var changed = update.apply(entity.toDomain());
        entity.apply(changed);
        return operations.saveAndFlush(entity).toDomain();
    }

    private void requireSuccessfulPriorStep(ShippingLabelOperation operation) {
        var priorStep =
                switch (operation.step()) {
                    case ADD_TO_CART -> null;
                    case PURCHASE -> ShippingLabelOperation.Step.ADD_TO_CART;
                    case GENERATE -> ShippingLabelOperation.Step.PURCHASE;
                };
        if (priorStep == null) {
            return;
        }
        var matches = operations.findByOrderIdAndShipmentUnitIdAndStep(
                operation.orderId(), operation.shipmentUnitId(), priorStep);
        var prior = matches.map(ShippingLabelOperationEntity::toDomain).orElse(null);
        if (prior == null
                || prior.state() != ShippingLabelOperation.State.SUCCEEDED
                || !operation.packageSequences().equals(prior.packageSequences())
                || !operation.providerShipmentId().equals(prior.providerShipmentId())) {
            throw new IllegalStateException("shipping operation requires a successful matching prior step");
        }
    }
}
