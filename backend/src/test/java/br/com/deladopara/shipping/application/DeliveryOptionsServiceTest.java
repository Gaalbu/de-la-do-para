package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteEntity;
import br.com.deladopara.shipping.adapter.persistence.ShippingQuoteRepository;
import br.com.deladopara.shipping.domain.PackageComposer;
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
    void normalizesCepAndReturnsOnlyPersistedOptions() throws Exception {
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
                manifest(1),
                now,
                now.plusSeconds(3600));
        var entity = new ShippingQuoteEntity(quote, "[1]", new ObjectMapper().writeValueAsString(quote.packages()));
        when(repository.findAllBySnapshotIdAndSnapshotVersionAndDestinationPostalCodeAndExpiresAtAfter(
                        eq(snapshotId), eq(3L), eq("66053000"), any(Instant.class)))
                .thenReturn(List.of(entity));
        var service = new DeliveryOptionsService(repository, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.findAvailable(snapshotId, 3, "66053-000"))
                .extracting(ShippingQuote::serviceId)
                .containsExactly("sandbox-pac");
    }

    @Test
    void rejectsSelectionWhenFingerprintDoesNotMatch() throws Exception {
        var repository = Mockito.mock(ShippingQuoteRepository.class);
        var now = Instant.parse("2026-09-22T12:00:00Z");
        var snapshotId = UUID.randomUUID();
        var quote = new ShippingQuote(
                UUID.randomUUID(),
                snapshotId,
                3,
                "66053000",
                "fingerprint",
                "pac",
                "PAC",
                1000,
                5,
                1,
                List.of(1),
                manifest(1),
                now,
                now.plusSeconds(3600));
        when(repository.findByIdAndSnapshotIdAndSnapshotVersion(eq(quote.id()), eq(snapshotId), eq(3L)))
                .thenReturn(java.util.Optional.of(new ShippingQuoteEntity(
                        quote, "[1]", new ObjectMapper().writeValueAsString(quote.packages()))));
        var service = new DeliveryOptionsService(repository, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.select(snapshotId, 3, quote.id(), "other-fingerprint"))
                .isInstanceOf(DeliveryOptionsService.SelectionConflictException.class);
    }

    private static List<ShippingQuote.PackageManifest> manifest(int sequence) {
        return List.of(ShippingQuote.PackageManifest.from(PackageComposer.compose(List.of(new PackageComposer.Line(
                        UUID.randomUUID(), PackageComposer.Category.CRAFT, false, sequence, 100, 80, 50, 500)))
                .get(0)));
    }
}
