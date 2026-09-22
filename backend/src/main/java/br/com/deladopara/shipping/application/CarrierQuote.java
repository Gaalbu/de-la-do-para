package br.com.deladopara.shipping.application;

import java.time.Instant;
import java.util.List;

public record CarrierQuote(
        String serviceId,
        String serviceName,
        long priceCents,
        int deliveryDays,
        Instant expiresAt,
        List<Integer> packageSequences) {

    public CarrierQuote {
        packageSequences = List.copyOf(packageSequences);
    }
}
