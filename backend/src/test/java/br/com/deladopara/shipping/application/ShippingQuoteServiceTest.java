package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

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

class ShippingQuoteServiceTest {

    @Test
    void persistsValidatedCarrierQuoteWithSnapshotIdentity() {
        var repository = Mockito.mock(ShippingQuoteRepository.class);
        var service = new ShippingQuoteService(
                repository, new ObjectMapper(), Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));
        var snapshotId = UUID.randomUUID();
        var packages = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.randomUUID(), PackageComposer.Category.FOOD, false, 2, 100, 80, 50, 500)));
        var quote = service.persist(
                snapshotId,
                3,
                "66053000",
                "fingerprint",
                new CarrierQuote(
                        "sandbox-pac", "Sandbox PAC", 2590, 5, Instant.parse("2026-09-22T13:00:00Z"), List.of(1)),
                packages,
                2);

        assertThat(quote.snapshotId()).isEqualTo(snapshotId);
        assertThat(quote.snapshotVersion()).isEqualTo(3);
        assertThat(quote.packages()).hasSize(1);
        assertThat(quote.packages().get(0).totalWeightGrams()).isEqualTo(1_150);
        assertThat(quote.packages().get(0).lines()).hasSize(2);
        verify(repository).save(any(ShippingQuoteEntity.class));
    }

    @Test
    void rejectsCarrierCoverageThatDoesNotMatchPackageComposition() {
        var service = new ShippingQuoteService(
                Mockito.mock(ShippingQuoteRepository.class),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC));
        var packages = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.randomUUID(), PackageComposer.Category.CRAFT, false, 1, 100, 80, 50, 500)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.persist(
                        UUID.randomUUID(),
                        1,
                        "66053000",
                        "fingerprint",
                        new CarrierQuote("pac", "PAC", 1000, 5, Instant.parse("2026-09-22T13:00:00Z"), List.of(99)),
                        packages,
                        1))
                .isInstanceOf(ShippingAdapterException.class)
                .hasMessage("carrier package coverage does not match accepted composition");
    }
}
