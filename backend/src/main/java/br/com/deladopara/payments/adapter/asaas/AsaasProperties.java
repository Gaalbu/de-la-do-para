package br.com.deladopara.payments.adapter.asaas;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Asaas sandbox settings ({@code payments.asaas.*}). The API key only comes from the environment and never appears in
 * messages. {@code storeUrl} is where the hosted page sends the buyer back; it never confirms a payment.
 */
@ConfigurationProperties(prefix = "payments.asaas")
public record AsaasProperties(
        URI baseUrl, String apiKey, URI storeUrl, int minutesToExpire, Duration timeout, Set<String> checkoutHosts) {

    /** Asaas accepts 10–1440 minutes; the link must also end before the 15-minute stock reservation. */
    static final int MIN_MINUTES = 10;

    static final int MAX_MINUTES = 15;

    public AsaasProperties {
        if (baseUrl == null || storeUrl == null || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Asaas configuration is incomplete");
        }
        if (!"https".equals(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Asaas base URL must use HTTPS");
        }
        if (minutesToExpire < MIN_MINUTES || minutesToExpire > MAX_MINUTES) {
            throw new IllegalArgumentException("Asaas link validity must be between 10 and 15 minutes");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Asaas timeout is invalid");
        }
        if (checkoutHosts == null || checkoutHosts.isEmpty()) {
            throw new IllegalArgumentException("Asaas checkout hosts are required");
        }
        checkoutHosts = Set.copyOf(checkoutHosts);
    }
}
