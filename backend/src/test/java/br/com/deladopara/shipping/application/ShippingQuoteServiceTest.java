package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShippingQuoteServiceTest {

    @Test
    void persistsValidatedCarrierQuoteWithSnapshotIdentity() {
        var repository = Mockito.mock(ShippingQuoteRepository.class);
        var service = new ShippingQuoteService(
                repository, new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));
        var snapshotId = UUID.randomUUID();
        var quote = service.persist(
                snapshotId,
                3,
                "66053000",
                "fingerprint",
                new CarrierQuote(
                        "sandbox-pac", "Sandbox PAC", 2590, 5, Instant.parse("2026-09-22T13:00:00Z"), List.of(1, 2)),
                2);

        assertThat(quote.snapshotId()).isEqualTo(snapshotId);
        assertThat(quote.snapshotVersion()).isEqualTo(3);
        verify(repository).save(any(ShippingQuoteEntity.class));
    }
}
