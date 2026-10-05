package br.com.deladopara.shipping.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.shipping.application.ShippingLabelOperationService;
import br.com.deladopara.shipping.domain.ShippingLabelOperation;
import br.com.deladopara.support.PostgresTestContainer;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ShippingLabelOperationRepositoryIT {

    private static final Instant CREATED_AT = Instant.parse("2026-10-03T12:00:00Z");

    private final ShippingLabelOperationRepository operations;
    private final ShippingLabelOperationService service;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    @Autowired
    ShippingLabelOperationRepositoryIT(
            ShippingLabelOperationRepository operations,
            ShippingLabelOperationService service,
            JdbcTemplate jdbc,
            EntityManager entityManager) {
        this.operations = operations;
        this.service = service;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Test
    @Transactional
    void storesAndReloadsAnOperationWithItsStablePackageMapping() {
        var orderId = insertPaidDeliveryOrder();
        var operation = ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                UUID.randomUUID(),
                List.of(1, 2),
                ShippingLabelOperation.Step.ADD_TO_CART,
                UUID.randomUUID(),
                CREATED_AT);

        operations.saveAndFlush(new ShippingLabelOperationEntity(operation));
        var reloaded = operations.findById(operation.id()).orElseThrow().toDomain();

        assertThat(reloaded).usingRecursiveComparison().isEqualTo(operation);
    }

    @Test
    @Transactional
    void rejectsLabelOperationForPickupOrder() {
        var orderId = insertOrder(FulfillmentMode.PICKUP, OrderStatus.PAID);
        var operation = operation(orderId, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("shipping labels require an eligible delivery order");
    }

    @Test
    @Transactional
    void rejectsLabelOperationBeforePaymentIsConfirmed() {
        var orderId = insertOrder(FulfillmentMode.DELIVERY, OrderStatus.PENDING_PAYMENT);
        var operation = operation(orderId, UUID.randomUUID());

        assertThatThrownBy(() -> service.create(operation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("shipping labels require an eligible delivery order");
    }

    @Test
    @Transactional
    void persistsTheKnownProviderIdAcrossPurchaseAndGenerationOperations() {
        var orderId = insertPaidDeliveryOrder();
        var shipmentUnitId = UUID.randomUUID();
        var addToCart = operation(orderId, shipmentUnitId);
        service.create(addToCart);
        service.request(addToCart.id(), CREATED_AT.plusSeconds(1));
        service.succeed(addToCart.id(), "carrier-shipment-42", CREATED_AT.plusSeconds(2));
        var purchase = followupOperation(orderId, shipmentUnitId, ShippingLabelOperation.Step.PURCHASE);
        var generate = followupOperation(orderId, shipmentUnitId, ShippingLabelOperation.Step.GENERATE);

        service.create(purchase);
        service.request(purchase.id(), CREATED_AT.plusSeconds(3));
        service.succeed(purchase.id(), null, CREATED_AT.plusSeconds(4));
        service.create(generate);
        entityManager.clear();

        assertThat(service.find(purchase.id()).providerShipmentId()).isEqualTo("carrier-shipment-42");
        assertThat(service.find(generate.id()).providerShipmentId()).isEqualTo("carrier-shipment-42");
    }

    @Test
    @Transactional
    void repeatedCreateReturnsExistingStateWithoutResettingAnInFlightOperation() {
        var orderId = insertPaidDeliveryOrder();
        var shipmentUnitId = UUID.randomUUID();
        var original = operation(orderId, shipmentUnitId);
        service.create(original);
        service.request(original.id(), CREATED_AT.plusSeconds(1));
        var repeated = ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                shipmentUnitId,
                original.packageSequences(),
                ShippingLabelOperation.Step.ADD_TO_CART,
                UUID.randomUUID(),
                CREATED_AT.plusSeconds(2));

        var result = service.create(repeated);

        assertThat(result.id()).isEqualTo(original.id());
        assertThat(result.state()).isEqualTo(ShippingLabelOperation.State.REQUESTED);
        assertThat(result.correlationId()).isEqualTo(original.correlationId());

        var changedMapping = ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                shipmentUnitId,
                List.of(3),
                ShippingLabelOperation.Step.ADD_TO_CART,
                UUID.randomUUID(),
                CREATED_AT.plusSeconds(2));
        assertThatThrownBy(() -> service.create(changedMapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shipping operation does not match the persisted package mapping");
    }

    @Test
    @Transactional
    void rejectsPurchaseWhenProviderIdOrPackageMappingDiffersFromSuccessfulCart() {
        var orderId = insertPaidDeliveryOrder();
        var shipmentUnitId = UUID.randomUUID();
        var addToCart = operation(orderId, shipmentUnitId);
        service.create(addToCart);
        service.request(addToCart.id(), CREATED_AT.plusSeconds(1));
        service.succeed(addToCart.id(), "carrier-shipment-42", CREATED_AT.plusSeconds(2));

        var mismatch = ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                shipmentUnitId,
                List.of(3),
                ShippingLabelOperation.Step.PURCHASE,
                UUID.randomUUID(),
                "different-shipment-id",
                CREATED_AT.plusSeconds(3));

        assertThatThrownBy(() -> service.create(mismatch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("shipping operation requires a successful matching prior step");
    }

    @Test
    @Transactional
    void rejectsGenerationUntilPurchaseSucceeded() {
        var orderId = insertPaidDeliveryOrder();
        var shipmentUnitId = UUID.randomUUID();
        var addToCart = operation(orderId, shipmentUnitId);
        service.create(addToCart);
        service.request(addToCart.id(), CREATED_AT.plusSeconds(1));
        service.succeed(addToCart.id(), "carrier-shipment-42", CREATED_AT.plusSeconds(2));
        var purchase = followupOperation(orderId, shipmentUnitId, ShippingLabelOperation.Step.PURCHASE);
        service.create(purchase);

        var generate = followupOperation(orderId, shipmentUnitId, ShippingLabelOperation.Step.GENERATE);
        assertThatThrownBy(() -> service.create(generate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("shipping operation requires a successful matching prior step");
    }

    @Test
    @Transactional
    void preventsCreatingAnotherOperationForTheSameOrderUnitAndStep() {
        var orderId = insertPaidDeliveryOrder();
        var shipmentUnitId = UUID.randomUUID();
        var first = operation(orderId, shipmentUnitId);
        var duplicate = operation(orderId, shipmentUnitId);
        operations.saveAndFlush(new ShippingLabelOperationEntity(first));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> operations.saveAndFlush(new ShippingLabelOperationEntity(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void persistsUnknownAcrossReloadAndRequiresExplicitReconciliation() {
        var operation = operation(insertPaidDeliveryOrder(), UUID.randomUUID());
        service.create(operation);
        service.request(operation.id(), CREATED_AT.plusSeconds(1));
        service.markUnknown(operation.id(), CREATED_AT.plusSeconds(30));
        entityManager.clear();

        assertThat(service.find(operation.id()).state()).isEqualTo(ShippingLabelOperation.State.UNKNOWN);
        assertThatThrownBy(() -> service.request(operation.id(), CREATED_AT.plusSeconds(31)))
                .isInstanceOf(IllegalStateException.class);

        var reconciled =
                service.reconcileSucceeded(operation.id(), "carrier-shipment-unknown-1", CREATED_AT.plusSeconds(60));
        entityManager.clear();

        assertThat(service.find(operation.id()).providerShipmentId()).isEqualTo("carrier-shipment-unknown-1");
        assertThat(reconciled.state()).isEqualTo(ShippingLabelOperation.State.SUCCEEDED);
    }

    @Test
    void allowsOnlyOneConcurrentWorkerToStartTheSameExternalWrite() throws Exception {
        var operation = operation(insertPaidDeliveryOrder(), UUID.randomUUID());
        service.create(operation);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> requestConcurrently(operation.id(), ready, start));
            var second = workers.submit(() -> requestConcurrently(operation.id(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(service.find(operation.id()).state()).isEqualTo(ShippingLabelOperation.State.REQUESTED);
    }

    private static ShippingLabelOperation operation(UUID orderId, UUID shipmentUnitId) {
        return ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                shipmentUnitId,
                List.of(1, 2),
                ShippingLabelOperation.Step.ADD_TO_CART,
                UUID.randomUUID(),
                CREATED_AT);
    }

    private static ShippingLabelOperation followupOperation(
            UUID orderId, UUID shipmentUnitId, ShippingLabelOperation.Step step) {
        return ShippingLabelOperation.ready(
                UUID.randomUUID(),
                orderId,
                shipmentUnitId,
                List.of(1, 2),
                step,
                UUID.randomUUID(),
                "carrier-shipment-42",
                CREATED_AT);
    }

    private boolean requestConcurrently(UUID operationId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            service.request(operationId, CREATED_AT.plusSeconds(1));
            return true;
        } catch (IllegalStateException | OptimisticLockingFailureException expected) {
            return false;
        }
    }

    private UUID insertPaidDeliveryOrder() {
        return insertOrder(FulfillmentMode.DELIVERY, OrderStatus.PAID);
    }

    private UUID insertOrder(FulfillmentMode mode, OrderStatus status) {
        var orderId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO purchase_order (id, checkout_key, contact_email, fulfillment_mode, currency,"
                        + " subtotal_cents, shipping_cents, discount_cents, total_cents, preparation_days,"
                        + " pricing_rule_version, destination, status, status_sequence, created_at, updated_at)"
                        + " VALUES (?, ?, 'shipping@example.com', ?, 'BRL', 1000, 0, 0, 1000, 0,"
                        + " 'test-v1', '{}'::jsonb, ?, 1, ?, ?)",
                orderId,
                "shipping-label-" + UUID.randomUUID(),
                mode.name(),
                status.name(),
                Timestamp.from(CREATED_AT),
                Timestamp.from(CREATED_AT));
        return orderId;
    }
}
