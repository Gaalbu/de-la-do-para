package br.com.deladopara.payments.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.deladopara.payments.application.ProviderEventInbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

/** The public webhook must reject before buffering: an unauthenticated or oversized body is never read whole. */
class AsaasWebhookControllerTest {

    private static final String TOKEN = "token-de-teste-do-webhook-asaas-0123456789";

    private final ProviderEventInbox inbox = mock(ProviderEventInbox.class);
    private final AsaasWebhookController controller = new AsaasWebhookController(inbox, new ObjectMapper(), TOKEN);

    @Test
    void missingTokenIsRejectedWithoutReadingTheBody() throws IOException {
        var body = new CountingStream(10 * 1024 * 1024);

        var response = controller.receive(null, request(body, -1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body.read).isZero();
        verifyNoInteractions(inbox);
    }

    @Test
    void declaredOversizedBodyIsRejectedWithoutReadingIt() throws IOException {
        var body = new CountingStream(10 * 1024 * 1024);

        var response = controller.receive(TOKEN, request(body, 10 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(body.read).isZero();
        verifyNoInteractions(inbox);
    }

    @Test
    void undeclaredOversizedBodyStopsReadingJustPastTheLimit() throws IOException {
        var body = new CountingStream(10 * 1024 * 1024);

        var response = controller.receive(TOKEN, request(body, -1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(body.read).isEqualTo(AsaasWebhookController.MAX_BODY_BYTES + 1L);
        verifyNoInteractions(inbox);
    }

    @Test
    void unconfiguredTokenRejectsEveryCall() throws IOException {
        var unconfigured = new AsaasWebhookController(inbox, new ObjectMapper(), "");

        var response = unconfigured.receive(TOKEN, request(new CountingStream(10), -1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(inbox);
    }

    private static MockHttpServletRequest request(ServletInputStream body, long contentLength) {
        var request = new MockHttpServletRequest("POST", "/api/v1/webhooks/asaas") {
            @Override
            public ServletInputStream getInputStream() {
                return body;
            }

            @Override
            public long getContentLengthLong() {
                return contentLength;
            }
        };
        request.setContentType("application/json");
        return request;
    }

    /** An endless-looking body of {@code size} bytes that records how much the controller consumed. */
    private static final class CountingStream extends ServletInputStream {

        private final long size;
        long read;

        CountingStream(long size) {
            this.size = size;
        }

        @Override
        public int read() {
            if (read >= size) {
                return -1;
            }
            read++;
            return 'x';
        }

        @Override
        public boolean isFinished() {
            return read >= size;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
