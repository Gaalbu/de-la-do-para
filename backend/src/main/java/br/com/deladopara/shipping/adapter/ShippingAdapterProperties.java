package br.com.deladopara.shipping.adapter;

import java.time.Duration;

public record ShippingAdapterProperties(
        String baseUrl,
        String credential,
        Duration timeout,
        Duration quoteValidity,
        String originPostalCode,
        String userAgent) {

    public ShippingAdapterProperties {
        if (baseUrl == null
                || !"sandbox.melhorenvio.com.br"
                        .equals(java.net.URI.create(baseUrl).getHost())
                || credential == null
                || credential.isBlank()
                || originPostalCode == null
                || !originPostalCode.matches("\\d{8}")
                || userAgent == null
                || userAgent.isBlank()) {
            throw new IllegalArgumentException("shipping adapter configuration is incomplete");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("shipping adapter timeout is invalid");
        }
        if (quoteValidity == null || quoteValidity.isZero() || quoteValidity.isNegative()) {
            throw new IllegalArgumentException("shipping quote validity is invalid");
        }
    }
}
