package br.com.deladopara.shipping.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.shipping.application.CarrierQuote;
import br.com.deladopara.shipping.application.DeliveryOptionsService;
import br.com.deladopara.shipping.application.ShippingQuote;
import br.com.deladopara.shipping.application.ShippingQuoteService;
import br.com.deladopara.shipping.domain.PackageComposer;
import br.com.deladopara.support.PostgresTestContainer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ShippingQuoteRepositoryIT {

    private final ShippingQuoteService quotes;
    private final DeliveryOptionsService options;

    @Autowired
    ShippingQuoteRepositoryIT(ShippingQuoteService quotes, DeliveryOptionsService options) {
        this.quotes = quotes;
        this.options = options;
    }

    @Test
    void persistsAndReloadsPackageManifestFromJsonb() {
        var snapshotId = UUID.randomUUID();
        var plans = PackageComposer.compose(List.of(new PackageComposer.Line(
                UUID.randomUUID(), PackageComposer.Category.CRAFT, false, 2, 100, 80, 50, 500)));
        var now = Instant.now();
        var quote = quotes.persist(
                snapshotId,
                1,
                "66053000",
                "fingerprint",
                new CarrierQuote("sandbox-pac", "Sandbox PAC", 2590, 5, now.plusSeconds(3600), List.of(1)),
                plans,
                2);

        var loaded = options.select(snapshotId, 1, quote.id(), "fingerprint");

        assertThat(loaded.packages()).containsExactlyElementsOf(quote.packages());
        assertThat(loaded.packages().get(0).totalWeightGrams()).isEqualTo(1_150);
        assertThat(loaded.packages().get(0).lines())
                .containsExactly(
                        new ShippingQuote.PackageLine(
                                plans.get(0).lines().get(0).skuId(), 1),
                        new ShippingQuote.PackageLine(
                                plans.get(0).lines().get(1).skuId(), 1));
    }
}
