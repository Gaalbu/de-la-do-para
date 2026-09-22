package br.com.deladopara.checkout.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.checkout.adapter.persistence.CheckoutSnapshotEntity;
import br.com.deladopara.checkout.application.CheckoutSnapshotService;
import java.time.Instant;
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
}
