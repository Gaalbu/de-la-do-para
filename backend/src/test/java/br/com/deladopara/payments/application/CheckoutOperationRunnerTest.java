package br.com.deladopara.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.payments.application.CheckoutOperations.Claimed;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutRequest;
import br.com.deladopara.payments.application.PaymentProvider.CheckoutState;
import br.com.deladopara.payments.application.PaymentProvider.CreatedCheckout;
import br.com.deladopara.payments.application.PaymentProvider.ProviderRejectedException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

/** Every outcome that is not a clean success must leave a diagnosable trace, never a silent UNKNOWN. */
class CheckoutOperationRunnerTest {

    private static final Claimed CLAIMED = new Claimed(
            UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            new CheckoutRequest(UUID.randomUUID(), UUID.randomUUID(), 5_250, Instant.parse("2026-09-24T12:15:00Z")));
    private static final CreatedCheckout CREATED =
            new CreatedCheckout("chk_42", "https://sandbox.example/c/chk_42", Instant.parse("2026-09-24T12:15:00Z"));

    private final Logger logger = (Logger) LoggerFactory.getLogger(CheckoutOperationRunner.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final CheckoutOperations operations = mock(CheckoutOperations.class);

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
        when(operations.claim()).thenReturn(Optional.of(CLAIMED));
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    private static PaymentProvider creating(Function<CheckoutRequest, CreatedCheckout> create) {
        return new PaymentProvider() {
            @Override
            public CreatedCheckout createCheckout(CheckoutRequest request) {
                return create.apply(request);
            }

            @Override
            public Optional<CheckoutState> findCheckout(UUID paymentIntentId) {
                return Optional.empty();
            }
        };
    }

    @Test
    void unknownOutcomeIsLoggedWithItsCause() {
        var cause = new IllegalStateException("connection reset");

        new CheckoutOperationRunner(operations, creating(request -> {
                    throw cause;
                }))
                .runNext();

        verify(operations).recordUnknown(eq(CLAIMED), eq("UNKNOWN:IllegalStateException"), any());
        var event = logs.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains(CLAIMED.operationId().toString());
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("connection reset");
    }

    @Test
    void rejectionIsLoggedWithTheProviderReason() {
        new CheckoutOperationRunner(operations, creating(request -> {
                    throw new ProviderRejectedException("HTTP_400");
                }))
                .runNext();

        verify(operations).recordRejected(eq(CLAIMED), eq("REJECTED:HTTP_400"), any());
        assertThat(logs.list.getFirst().getFormattedMessage())
                .contains(CLAIMED.operationId().toString())
                .contains("HTTP_400");
    }

    @Test
    void checkoutCreatedButNotRecordedKeepsTheProviderIdForReconciliation() {
        doThrow(new DataAccessResourceFailureException("database down"))
                .when(operations)
                .recordCreated(eq(CLAIMED), eq(CREATED), any());

        new CheckoutOperationRunner(operations, creating(request -> CREATED)).runNext();

        verify(operations).recordUnknown(eq(CLAIMED), eq("UNKNOWN:DataAccessResourceFailureException"), any());
        var event = logs.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("chk_42")
                .contains(CLAIMED.operationId().toString())
                .doesNotContain(CREATED.url());
        assertThat(event.getThrowableProxy()).isNotNull();
    }
}
