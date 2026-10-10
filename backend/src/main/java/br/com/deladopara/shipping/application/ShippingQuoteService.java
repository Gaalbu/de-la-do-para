package br.com.deladopara.shipping.application;

import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteRepository;
import br.com.deladopara.shipping.domain.PackageComposer.PackagePlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
            List<PackagePlan> packagePlans,
            int preparationDays) {
        var now = Instant.now(clock);
        var manifests =
                packagePlans.stream().map(ShippingQuote.PackageManifest::from).toList();
        var expectedSequences =
                manifests.stream().map(ShippingQuote.PackageManifest::sequence).toList();
        if (carrierQuote.packageSequences().size() != expectedSequences.size()
                || new HashSet<>(carrierQuote.packageSequences()).size()
                        != carrierQuote.packageSequences().size()
                || !new HashSet<>(carrierQuote.packageSequences()).equals(new HashSet<>(expectedSequences))) {
            throw new ShippingAdapterException("carrier package coverage does not match accepted composition");
        }
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
                expectedSequences,
                manifests,
                now,
                carrierQuote.expiresAt());
        try {
            var entity = new ShippingQuoteEntity(
                    quote,
                    objectMapper.writeValueAsString(quote.packageSequences()),
                    objectMapper.writeValueAsString(quote.packages()));
            quotes.save(entity);
            return quote;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("shipping package sequence serialization failed", exception);
        }
    }
}
