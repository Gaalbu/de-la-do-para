package br.com.deladopara.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final Pattern UUID_HEADER =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var supplied = request.getHeader("X-Request-ID");
        var id = supplied != null && UUID_HEADER.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        var previous = MDC.get("correlationId");
        MDC.put("correlationId", id);
        response.setHeader("X-Request-ID", id);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            try {
                var route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                LOG.atInfo()
                        .addKeyValue("method", METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER")
                        .addKeyValue("route", route == null ? "UNMATCHED" : route.toString())
                        .addKeyValue("status", completed ? response.getStatus() : 500)
                        .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000.0)
                        .log("http_request_completed");
            } finally {
                if (previous == null) {
                    MDC.remove("correlationId");
                } else {
                    MDC.put("correlationId", previous);
                }
            }
        }
    }
}
