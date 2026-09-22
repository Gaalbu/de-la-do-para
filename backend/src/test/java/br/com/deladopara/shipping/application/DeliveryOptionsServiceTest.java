package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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

class DeliveryOptionsServiceTest {

    @Test
    void normalizesCepAndReturnsOnlyPersistedOptions() {
        var repository = Mockito.mock(ShippingQuoteRepository.class);
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var snapshotId = UUID.randomUUID();
        var quote = new ShippingQuote(
                UUID.randomUUID(),
                snapshotId,
                3,
                "66053000",
                "fingerprint",
                "sandbox-pac",
                "Sandbox PAC",
                2590,
                5,
                2,
                List.of(1),
                now,
                now.plusSeconds(3600));
        var entity = new ShippingQuoteEntity(quote, "[1]");
        when(repository.findAllBySnapshotIdAndSnapshotVersionAndDestinationPostalCodeAndExpiresAtAfter(
                        eq(snapshotId), eq(3L), eq("66053000"), any(Instant.class)))
                .thenReturn(List.of(entity));
        var service = new DeliveryOptionsService(repository, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.findAvailable(snapshotId, 3, "66053-000"))
                .extracting(ShippingQuote::serviceId)
                .containsExactly("sandbox-pac");
    }
}
