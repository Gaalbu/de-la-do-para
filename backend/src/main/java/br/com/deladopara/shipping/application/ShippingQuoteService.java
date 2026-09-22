package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingQuoteService {

    private final ShippingQuoteRepository quotes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ShippingQuoteService(ShippingQuoteRepository quotes, ObjectMapper objectMapper, Clock clock) {
        this.quotes = quotes;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ShippingQuote persist(
            UUID snapshotId,
            long snapshotVersion,
            String destinationPostalCode,
            String inputFingerprint,
            CarrierQuote carrierQuote,
            int preparationDays) {
        var now = Instant.now(clock);
        if (!carrierQuote.expiresAt().isAfter(now)) {
            throw new ShippingAdapterException("carrier quote is expired");
        }
        var quote = new ShippingQuote(
                UUID.randomUUID(),
                snapshotId,
                snapshotVersion,
                destinationPostalCode,
                inputFingerprint,
                carrierQuote.serviceId(),
                carrierQuote.serviceName(),
                carrierQuote.priceCents(),
                carrierQuote.deliveryDays(),
                preparationDays,
                carrierQuote.packageSequences(),
                now,
                carrierQuote.expiresAt());
        try {
            var entity = new ShippingQuoteEntity(quote, objectMapper.writeValueAsString(quote.packageSequences()));
            quotes.save(entity);
            return quote;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("shipping package sequence serialization failed", exception);
        }
    }
}
