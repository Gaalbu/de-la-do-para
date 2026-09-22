package br.com.deladopara.checkout.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.application.CheckoutSnapshotService;
import br.com.deladopara.shipping.application.ShippingQuote;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class CheckoutControllerTest {

    @Test
    void returnsSnapshotIdentityAndVersionForCurrentSession() {
        var service = mock(CheckoutSnapshotService.class);
        var snapshotId = UUID.randomUUID();
        var snapshot = new CheckoutSnapshotEntity(
                snapshotId, UUID.randomUUID(), 4, "session-key", "[]", Instant.parse("2026-09-22T12:00:00Z"));
        when(service.start("session-id")).thenReturn(snapshot);
        var controller = new CheckoutController(service);
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "session-id"));

        var response = controller.start(request);

        assertThat(response.snapshotId()).isEqualTo(snapshotId);
        assertThat(response.snapshotVersion()).isEqualTo(4);
    }

    @Test
    void returnsDeliveryOptionsForCurrentSession() {
        var service = mock(CheckoutSnapshotService.class);
        var snapshotId = UUID.randomUUID();
        var quote = new ShippingQuote(
                UUID.randomUUID(),
                snapshotId,
                4,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                List.of(1),
                Instant.parse("2026-09-22T12:00:00Z"),
                Instant.parse("2026-09-22T13:00:00Z"));
        when(service.findDeliveryOptions("session-id", snapshotId, 4, "66053-000"))
                .thenReturn(List.of(quote));
        var controller = new CheckoutController(service);
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "session-id"));

        var response = controller.deliveryOptions(snapshotId, 4, "66053-000", request);

        assertThat(response.options()).containsExactly(quote);
        assertThat(response.inputFingerprint()).isEqualTo("fingerprint");
    }

    @Test
    void returnsValidatedDeliverySelection() {
        var service = mock(CheckoutSnapshotService.class);
        var snapshotId = UUID.randomUUID();
        var quoteId = UUID.randomUUID();
        var quote = new ShippingQuote(
                quoteId,
                snapshotId,
                4,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                List.of(1),
                Instant.parse("2026-09-22T12:00:00Z"),
                Instant.parse("2026-09-22T13:00:00Z"));
        when(service.selectDeliveryOption("session-id", snapshotId, 4, quoteId, "fingerprint"))
                .thenReturn(quote);
        var controller = new CheckoutController(service);
        var request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession(null, "session-id"));

        var response = controller.selectDeliveryOption(
                snapshotId, 4, new CheckoutController.SelectionRequest(quoteId, "fingerprint"), request);

        assertThat(response.quoteId()).isEqualTo(quoteId);
        assertThat(response.inputFingerprint()).isEqualTo("fingerprint");
    }
}
