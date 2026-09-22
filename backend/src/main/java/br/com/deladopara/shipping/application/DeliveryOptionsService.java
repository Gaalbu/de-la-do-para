package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryOptionsService {

    private final ShippingQuoteRepository quotes;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeliveryOptionsService(ShippingQuoteRepository quotes, ObjectMapper objectMapper, Clock clock) {
        this.quotes = quotes;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ShippingQuote> findAvailable(UUID snapshotId, long snapshotVersion, String destinationPostalCode) {
        var now = Instant.now(clock);
        return quotes
                .findAllBySnapshotIdAndSnapshotVersionAndDestinationPostalCodeAndExpiresAtAfter(
                        snapshotId, snapshotVersion, normalizePostalCode(destinationPostalCode), now)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private ShippingQuote toDomain(ShippingQuoteEntity entity) {
        try {
            var sequences = objectMapper.readValue(entity.getPackageSequences(), new TypeReference<List<Integer>>() {});
            return entity.toDomain(sequences);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored shipping package sequence is invalid", exception);
        }
    }

    private static String normalizePostalCode(String postalCode) {
        if (postalCode == null) {
            throw new IllegalArgumentException("destination postal code is required");
        }
        var normalized = postalCode.replace("-", "").trim();
        if (!normalized.matches("\\d{8}")) {
            throw new IllegalArgumentException("destination postal code is invalid");
        }
        return normalized;
    }
}
