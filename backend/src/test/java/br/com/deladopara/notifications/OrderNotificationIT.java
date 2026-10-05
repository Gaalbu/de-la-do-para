package br.com.deladopara.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.deladopara.eventing.application.EventConsumptionService;
import br.com.deladopara.eventing.application.EventEnvelope;
import br.com.deladopara.eventing.application.EventRetryPolicy;
import br.com.deladopara.notifications.adapter.persistence.OrderNotificationRepository;
import br.com.deladopara.orders.application.CreateOrderCommand;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import br.com.deladopara.support.PostgresTestContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Import(PostgresTestContainer.class)
@Testcontainers
class OrderNotificationIT {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025).forStatusCode(200));

    @DynamicPropertySource
    static void smtpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    private final EventConsumptionService consumption;
    private final OrderService orders;
    private final OrderNotificationRepository notifications;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final br.com.deladopara.notifications.application.OrderNotificationWorker worker;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final JavaMailSender smtpMailSender;

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Autowired
    OrderNotificationIT(
            EventConsumptionService consumption,
            OrderService orders,
            OrderNotificationRepository notifications,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            JavaMailSender smtpMailSender) {
        this.consumption = consumption;
        this.orders = orders;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactionManager = transactionManager;
        this.smtpMailSender = smtpMailSender;
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(ignored -> new MimeMessage(Session.getInstance(new Properties())));
        this.worker = new br.com.deladopara.notifications.application.OrderNotificationWorker(
                notifications,
                mailSender,
                java.time.Clock.systemUTC(),
                java.time.Duration.ofMinutes(2),
                "no-reply@deladopara.local",
                1,
                transactionManager,
                new EventRetryPolicy(new java.util.Random(1)));
    }

    @BeforeEach
    void clean() {
        jdbc.execute("ALTER TABLE purchase_order_item DISABLE TRIGGER purchase_order_item_immutable");
        jdbc.execute("ALTER TABLE purchase_order_status_history DISABLE TRIGGER purchase_order_history_immutable");
        jdbc.execute("ALTER TABLE purchase_order DISABLE TRIGGER purchase_order_snapshot_guard");
        jdbc.execute("TRUNCATE order_notification_outbox, event_consumption, event_consumer_cursor, "
                + "purchase_order_status_history, purchase_order_item, purchase_order, event_outbox CASCADE");
        jdbc.execute("ALTER TABLE purchase_order_item ENABLE TRIGGER purchase_order_item_immutable");
        jdbc.execute("ALTER TABLE purchase_order_status_history ENABLE TRIGGER purchase_order_history_immutable");
        jdbc.execute("ALTER TABLE purchase_order ENABLE TRIGGER purchase_order_snapshot_guard");
    }

    private UUID createOrder() {
        var destination = mapper.createObjectNode()
                .put("label", "Ponto de demonstração — Belém")
                .put("window", "segunda a sexta, das 9h às 18h (horário de Belém)");
        return orders.create(new CreateOrderCommand(
                        "notification-" + UUID.randomUUID(),
                        null,
                        "notify@example.test",
                        FulfillmentMode.PICKUP,
                        1200,
                        0,
                        null,
                        null,
                        0,
                        null,
                        1200,
                        1,
                        null,
                        "pricing-v1",
                        destination,
                        List.of(new CreateOrderCommand.Item(
                                UUID.randomUUID(), "Produto de teste", "unidade", 1, 1200, 1200)),
                        UUID.randomUUID()))
                .id();
    }

    @Test
    void consumesCreatedAndPaidEventsOnceAndQueuesOnlyCustomerSafeFacts() {
        var orderId = createOrder();
        var created = envelope("order.created", orderId, 0, mapper.createObjectNode());
        assertThat(consumption.consume(created).name()).isEqualTo("APPLIED");
        assertThat(consumption.consume(created).name()).isEqualTo("DUPLICATE");

        orders.transition(orderId, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        var paid = envelope(
                "order.status_changed", orderId, 1, mapper.createObjectNode().put("to", "PAID"));
        assertThat(consumption.consume(paid).name()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_notification_outbox", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT recipient) FROM order_notification_outbox", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE status='PENDING'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE recipient='notify@example.test'",
                        Integer.class))
                .isEqualTo(2);
    }

    @Test
    void pickupReadyEmailIncludesApprovedPointAndWindowButKeepsCodeOnSecureOrderPage() throws Exception {
        var orderId = createOrder();
        assertThat(consumption
                        .consume(envelope("order.created", orderId, 0, mapper.createObjectNode()))
                        .name())
                .isEqualTo("APPLIED");

        orders.transition(orderId, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed", orderId, 1, mapper.createObjectNode().put("to", "PAID")));
        orders.transition(orderId, OrderStatus.PREPARING, OrderActor.ADMIN, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed", orderId, 2, mapper.createObjectNode().put("to", "PREPARING")));
        orders.transition(orderId, OrderStatus.READY_FOR_PICKUP, OrderActor.ADMIN, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed", orderId, 3, mapper.createObjectNode().put("to", "READY_FOR_PICKUP")));

        for (var index = 0; index < 4; index++) {
            worker.poll();
        }

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.times(4)).send(captor.capture());
        var messages = captor.getAllValues();
        var readyParts = new java.util.ArrayList<String>();
        var createdParts = new java.util.ArrayList<String>();
        collectTextParts(messages.get(3), readyParts);
        collectTextParts(messages.getFirst(), createdParts);
        var readyContent = String.join("\n", readyParts);

        assertThat(readyContent)
                .contains(
                        "Ponto de retirada: Ponto de demonstração — Belém",
                        "Horário de retirada: segunda a sexta, das 9h às 18h (horário de Belém)",
                        "Código de retirada: consulte somente na tela segura do pedido; não o enviamos por e-mail.")
                .doesNotContain("pickup-code", "X-Order-Token", "http://", "https://");
        assertThat(String.join("\n", createdParts)).doesNotContain("Ponto de retirada:", "Horário de retirada:");
    }

    @Test
    void intermediatePaymentStatusChangesDoNotQueueCommercialNotifications() {
        var orderId = createOrder();
        var paymentIntentId = UUID.randomUUID();

        assertThat(consumption
                        .consume(envelope(
                                "payment.status_changed",
                                paymentIntentId,
                                0,
                                mapper.createObjectNode()
                                        .put("orderId", orderId.toString())
                                        .put("paymentIntentId", paymentIntentId.toString())
                                        .put("to", "AWAITING_PAYMENT")))
                        .name())
                .isEqualTo("APPLIED");
        assertThat(consumption
                        .consume(envelope(
                                "payment.status_changed",
                                paymentIntentId,
                                1,
                                mapper.createObjectNode()
                                        .put("orderId", orderId.toString())
                                        .put("paymentIntentId", paymentIntentId.toString())
                                        .put("to", "UNKNOWN")))
                        .name())
                .isEqualTo("APPLIED");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE order_id=?", Integer.class, orderId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM event_consumption WHERE aggregate_id=? AND processing_result='APPLIED'",
                        Integer.class,
                        paymentIntentId.toString()))
                .isEqualTo(2);
    }

    @Test
    void reviewCancellationAndExpirationEmailsUseNeutralPersistedStatuses() throws Exception {
        var reviewOrderId = createOrder();
        consumption.consume(envelope("order.created", reviewOrderId, 0, mapper.createObjectNode()));
        orders.transition(
                reviewOrderId, OrderStatus.UNDER_REVIEW, OrderActor.SYSTEM, "LATE_PAYMENT", UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed",
                reviewOrderId,
                1,
                mapper.createObjectNode().put("to", "UNDER_REVIEW")));
        orders.transition(reviewOrderId, OrderStatus.CANCELLED, OrderActor.ADMIN, "REVIEW_DECISION", UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed",
                reviewOrderId,
                2,
                mapper.createObjectNode().put("to", "CANCELLED")));

        var expiredOrderId = createOrder();
        consumption.consume(envelope("order.created", expiredOrderId, 0, mapper.createObjectNode()));
        orders.transition(expiredOrderId, OrderStatus.EXPIRED, OrderActor.SYSTEM, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed",
                expiredOrderId,
                1,
                mapper.createObjectNode().put("to", "EXPIRED")));

        for (var index = 0; index < 5; index++) {
            worker.poll();
        }

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, org.mockito.Mockito.times(5)).send(captor.capture());
        var textParts = new java.util.ArrayList<String>();
        for (var message : captor.getAllValues()) {
            collectTextParts(message, textParts);
        }
        var content = String.join("\n", textParts);
        assertThat(textParts).hasSize(10);
        assertThat(content)
                .contains("Estado: UNDER_REVIEW", "Estado: CANCELLED", "Estado: EXPIRED", "Consulte o pedido")
                .doesNotContain("reembolsado", "reembolso concluído", "refund", "http://", "https://");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE order_id IN (?,?) AND status='ACCEPTED'",
                        Integer.class,
                        reviewOrderId,
                        expiredOrderId))
                .isEqualTo(5);
    }

    @Test
    void smtpFailureIsDurablyRetriedAndPaymentStateDoesNotDependOnMail() {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        orders.transition(orderId, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed", orderId, 1, mapper.createObjectNode().put("to", "PAID")));
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id=?", String.class, orderId))
                .isEqualTo("PAID");
        doThrow(new MailSendException("SMTP connection failed", new ConnectException("Connection refused")))
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        var attemptStartedAt = java.time.Instant.now();
        worker.poll();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_order WHERE id=?", String.class, orderId))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE order_id=? AND attempt_count=1",
                        Integer.class,
                        orderId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE last_error_code LIKE '%connection%'",
                        Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT EXTRACT(EPOCH FROM (available_at - ?)) * 1000 "
                                + "FROM order_notification_outbox WHERE order_id=? AND attempt_count=1",
                        Double.class,
                        java.sql.Timestamp.from(attemptStartedAt),
                        orderId))
                .isBetween(0.0, 2000.0);
    }

    @Test
    void ambiguousSmtpSendIsMarkedUnknownAndNeverRetriedAutomatically() {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        doThrow(new MailSendException("connection lost after DATA"))
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        worker.poll();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM order_notification_outbox WHERE order_id=?", String.class, orderId))
                .isEqualTo("UNKNOWN");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notification_outbox WHERE status='PENDING'", Integer.class))
                .isZero();
    }

    @Test
    void doesNotSendLaterOrderUpdatesWhileEarlierNotificationIsUnknown() {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        orders.transition(orderId, OrderStatus.PAID, OrderActor.SYSTEM, null, UUID.randomUUID());
        consumption.consume(envelope(
                "order.status_changed", orderId, 1, mapper.createObjectNode().put("to", "PAID")));
        doThrow(new MailSendException("SMTP timed out after DATA", new SocketTimeoutException()))
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        worker.poll();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM order_notification_outbox WHERE order_id=? AND event_type='order.created'",
                        String.class,
                        orderId))
                .isEqualTo("UNKNOWN");
        assertThat(worker.claim()).isNull();
    }

    @Test
    void permanentSmtpFailureIsNotRetried() {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        doThrow(new MailAuthenticationException("credentials are private"))
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
        worker.poll();
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM order_notification_outbox WHERE order_id=?", String.class, orderId))
                .isEqualTo("FAILED");
    }

    @Test
    void acceptedMailContainsAccessiblePlainTextAndHtmlWithoutAccessTokenOrPickupCode() throws Exception {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        worker.poll();
        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        assertThat(message.getContent()).isInstanceOf(MimeMultipart.class);
        var body = (MimeMultipart) message.getContent();
        var textParts = new java.util.ArrayList<String>();
        collectTextParts(message, textParts);
        assertThat(textParts).hasSize(2);
        assertThat(textParts.get(0))
                .contains("demonstração", orderId.toString(), "R$")
                .doesNotContain("pickup-code");
        assertThat(textParts.get(1))
                .contains("<html>", orderId.toString(), "De Lá do Pará")
                .doesNotContain("pickup-code", "X-Order-Token", "http://", "https://");
        assertThat(body.getContentType()).contains("multipart");
        assertThat(jdbc.queryForObject(
                        "SELECT status FROM order_notification_outbox WHERE order_id=?", String.class, orderId))
                .isEqualTo("ACCEPTED");
    }

    @Test
    void configuredSmtpWorkerDeliversMessageToMailpit() throws Exception {
        var orderId = createOrder();
        consumption.consume(envelope("order.created", orderId, 0, mapper.createObjectNode()));
        var liveWorker = new br.com.deladopara.notifications.application.OrderNotificationWorker(
                notifications,
                smtpMailSender,
                java.time.Clock.systemUTC(),
                java.time.Duration.ofMinutes(2),
                "no-reply@deladopara.local",
                1,
                transactionManager,
                new EventRetryPolicy(new java.util.Random(1)));

        liveWorker.poll();

        assertThat(jdbc.queryForObject(
                        "SELECT status FROM order_notification_outbox WHERE order_id=?", String.class, orderId))
                .isEqualTo("ACCEPTED");
        var raw = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://" + MAILPIT.getHost() + ":"
                                        + MAILPIT.getMappedPort(8025) + "/api/v1/message/latest/raw"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(raw.statusCode()).isEqualTo(200);
        assertThat(raw.body())
                .contains("notify@example.test", orderId.toString(), "text/plain", "text/html")
                .doesNotContain("X-Order-Token", "pickup-code", "https://", "http://");
    }

    private EventEnvelope envelope(
            String type, UUID orderId, long version, com.fasterxml.jackson.databind.JsonNode payload) {
        return new EventEnvelope(
                UUID.randomUUID(),
                type,
                1,
                orderId.toString(),
                version,
                java.time.Instant.now().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                payload);
    }

    private static void collectTextParts(Part part, List<String> textParts) throws Exception {
        var content = part.getContent();
        if (content instanceof MimeMultipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                collectTextParts(multipart.getBodyPart(index), textParts);
            }
        } else if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            textParts.add(content.toString());
        }
    }
}
