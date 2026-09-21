package br.com.deladopara.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
        events.stop();
        MDC.clear();
    }

    @Test
    void createsCorrelationIdAndClearsContext() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/missing"), response, (request, reply) -> {
            assertThat(MDC.get("correlationId")).isEqualTo(response.getHeader("X-Request-ID"));
        });
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(events.list).hasSize(1);
    }

    @Test
    void preservesValidIdAndRestoresPreviousContext() throws Exception {
        var request = new MockHttpServletRequest("GET", "/test");
        var id = UUID.randomUUID().toString();
        request.addHeader("X-Request-ID", id);
        MDC.put("correlationId", "outer-context");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(id);
        assertThat(MDC.get("correlationId")).isEqualTo("outer-context");
    }

    @Test
    void excludesRequestSecretsAndUsesRouteTemplate() throws Exception {
        var request = new MockHttpServletRequest("POST", "/orders/private-id");
        request.addHeader("X-Request-ID", "secret@example.test\nforged-log");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("Cookie", "DLSESSION=private-cookie");
        request.setQueryString("token=private-query");
        request.setContent("private-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
            response.setStatus(409);
        });
        var event = events.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("http_request_completed");
        var fields = event.getKeyValuePairs().toString();
        assertThat(fields).contains("/orders/{id}", "409", "durationMs");
        assertThat(fields).doesNotContain("private", "secret", "forged");
        assertThat(event.getThrowableProxy()).isNull();
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull();
    }

    @Test
    void logsFailureWithoutExceptionMessageAndRestoresContext() {
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() ->
                        filter.doFilter(new MockHttpServletRequest("GET", "/private-token"), response, (req, res) -> {
                            throw new ServletException("secret exception data");
                        }))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get("correlationId")).isNull();
        var event = events.list.getFirst();
        assertThat(event.getKeyValuePairs().toString())
                .contains("500", "UNMATCHED")
                .doesNotContain("secret", "private");
        assertThat(event.getThrowableProxy()).isNull();
    }
}
