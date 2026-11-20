package br.com.deladopara.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import br.com.deladopara.pricing.application.SkuPriceService;
import br.com.deladopara.pricing.domain.Money;
import br.com.deladopara.pricing.domain.SkuPrice;
import br.com.deladopara.shipping.application.DeliveryOptionsService.SelectionConflictException;
import br.com.deladopara.shipping.domain.PackageComposer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class ShippingQuoteGenerationServiceTest {

    private static final UUID SKU = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void rejectsSelectionWhenCurrentCatalogPriceNoLongerMatchesQuoteFingerprint() {
        var sku = activeFoodSku();
        var skuRepository = Mockito.mock(ProductSkuRepository.class);
        when(skuRepository.findAllByIdIn(List.of(SKU))).thenReturn(List.of(sku));
        var prices = Mockito.mock(SkuPriceService.class);
        when(prices.findCurrentPrices(List.of(SKU))).thenReturn(Map.of(SKU, new SkuPrice(SKU, Money.brl(1900))));
        var service = generationService(skuRepository, prices);
        var snapshotId = UUID.randomUUID();
        var snapshotVersion = 7L;
        var items = serializedItems();
        var selectedQuote = quote(snapshotId, snapshotVersion, "fingerprint-created-at-1800-cents");

        assertThatThrownBy(() -> service.assertCurrent(snapshotId, snapshotVersion, items, selectedQuote))
                .isInstanceOf(SelectionConflictException.class);
    }

    @Test
    void acceptsSelectionWhenCurrentCatalogInputMatchesQuoteFingerprint() {
        var sku = activeFoodSku();
        var skuRepository = Mockito.mock(ProductSkuRepository.class);
        when(skuRepository.findAllByIdIn(List.of(SKU))).thenReturn(List.of(sku));
        var prices = Mockito.mock(SkuPriceService.class);
        when(prices.findCurrentPrices(List.of(SKU))).thenReturn(Map.of(SKU, new SkuPrice(SKU, Money.brl(1800))));
        var snapshotId = UUID.randomUUID();
        var snapshotVersion = 7L;
        var items = serializedItems();
        var adapter = Mockito.mock(FreightQuoteAdapter.class);
        var carrierQuote = new CarrierQuote("1", "PAC", 1500, 4, NOW.plusSeconds(300), List.of(1));
        when(adapter.quote(any(), eq(NOW))).thenReturn(List.of(carrierQuote));
        var quoteService = Mockito.mock(ShippingQuoteService.class);
        var quoteId = UUID.randomUUID();
        var quoteCreatedByPersistence = new org.mockito.stubbing.Answer<List<ShippingQuote>>() {
            @Override
            public List<ShippingQuote> answer(org.mockito.invocation.InvocationOnMock invocation) {
                var fingerprint = invocation.getArgument(3, String.class);
                return List.of(new ShippingQuote(
                        quoteId,
                        snapshotId,
                        snapshotVersion,
                        "66053000",
                        fingerprint,
                        "1",
                        "PAC",
                        1500,
                        4,
                        1,
                        List.of(1),
                        NOW,
                        NOW.plusSeconds(300)));
            }
        };
        when(quoteService.persistAll(any(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenAnswer(quoteCreatedByPersistence);
        var serviceWithAdapter = generationService(skuRepository, prices, adapter, quoteService);
        var saved = serviceWithAdapter.findOrCreate(snapshotId, snapshotVersion, items, "66053-000");

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().inputFingerprint()).isNotBlank();
        var selectedQuote = saved.getFirst();
        serviceWithAdapter.assertCurrent(snapshotId, snapshotVersion, items, selectedQuote);
    }

    @Test
    void composesSnapshotSkuWithCurrentPriceAndPersistsCarrierQuote() {
        var sku = Mockito.mock(ProductSku.class);
        when(sku.getId()).thenReturn(SKU);
        when(sku.isActive()).thenReturn(true);
        when(sku.getProductCategory()).thenReturn(Product.Category.FOOD);
        when(sku.getLengthMm()).thenReturn(200);
        when(sku.getWidthMm()).thenReturn(140);
        when(sku.getHeightMm()).thenReturn(50);
        when(sku.getGrossWeightGrams()).thenReturn(520);

        var skuRepository = Mockito.mock(ProductSkuRepository.class);
        when(skuRepository.findAllByIdIn(List.of(SKU))).thenReturn(List.of(sku));
        var prices = Mockito.mock(SkuPriceService.class);
        when(prices.findCurrentPrices(List.of(SKU))).thenReturn(Map.of(SKU, new SkuPrice(SKU, Money.brl(1800))));
        var adapter = Mockito.mock(FreightQuoteAdapter.class);
        var adapterProvider = Mockito.mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        var quote = new CarrierQuote("1", "PAC", 1500, 4, NOW.plusSeconds(300), List.of(1));
        when(adapter.quote(any(), eq(NOW))).thenReturn(List.of(quote));
        var persisted = Mockito.mock(ShippingQuote.class);
        var quoteService = Mockito.mock(ShippingQuoteService.class);
        when(quoteService.persistAll(any(), anyLong(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of(persisted));
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        when(deliveryOptions.findAvailable(any(), anyLong(), anyString())).thenReturn(List.of());
        var service = new ShippingQuoteGenerationService(
                skuRepository,
                prices,
                adapterProvider,
                quoteService,
                deliveryOptions,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "66053000");
        var snapshotId = UUID.randomUUID();
        var items = serializedItems();

        var result = service.findOrCreate(snapshotId, 7, items, "66053-000");

        assertThat(result).containsExactly(persisted);
        var request = ArgumentCaptor.forClass(ShippingQuoteRequest.class);
        verify(adapter).quote(request.capture(), eq(NOW));
        assertThat(request.getValue().originPostalCode()).isEqualTo("66053000");
        assertThat(request.getValue().destinationPostalCode()).isEqualTo("66053000");
        assertThat(request.getValue().insuranceValueCentsByPackage()).containsEntry(1, 1800L);
        PackageComposer.PackagePlan packagePlan = request.getValue().packages().getFirst();
        assertThat(packagePlan.totalWeightGrams()).isEqualTo(720);
        verify(quoteService).persistAll(eq(snapshotId), eq(7L), eq("66053000"), anyString(), eq(List.of(quote)), eq(1));
    }

    private static ProductSku activeFoodSku() {
        var sku = Mockito.mock(ProductSku.class);
        when(sku.getId()).thenReturn(SKU);
        when(sku.isActive()).thenReturn(true);
        when(sku.getProductCategory()).thenReturn(Product.Category.FOOD);
        when(sku.getLengthMm()).thenReturn(200);
        when(sku.getWidthMm()).thenReturn(140);
        when(sku.getHeightMm()).thenReturn(50);
        when(sku.getGrossWeightGrams()).thenReturn(520);
        return sku;
    }

    private static ShippingQuoteGenerationService generationService(
            ProductSkuRepository skuRepository, SkuPriceService prices) {
        return generationService(skuRepository, prices, List.of());
    }

    private static ShippingQuoteGenerationService generationService(
            ProductSkuRepository skuRepository, SkuPriceService prices, List<ShippingQuote> availableQuotes) {
        var adapterProvider = Mockito.mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(null);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        when(deliveryOptions.findAvailable(any(), anyLong(), anyString())).thenReturn(List.of());
        return new ShippingQuoteGenerationService(
                skuRepository,
                prices,
                adapterProvider,
                Mockito.mock(ShippingQuoteService.class),
                deliveryOptions,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "66053000");
    }

    private static ShippingQuoteGenerationService generationService(
            ProductSkuRepository skuRepository, SkuPriceService prices, FreightQuoteAdapter adapter) {
        return generationService(skuRepository, prices, adapter, Mockito.mock(ShippingQuoteService.class));
    }

    private static ShippingQuoteGenerationService generationService(
            ProductSkuRepository skuRepository,
            SkuPriceService prices,
            FreightQuoteAdapter adapter,
            ShippingQuoteService quoteService) {
        var adapterProvider = Mockito.mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        var deliveryOptions = Mockito.mock(DeliveryOptionsService.class);
        when(deliveryOptions.findAvailable(any(), anyLong(), anyString())).thenReturn(List.of());
        return new ShippingQuoteGenerationService(
                skuRepository,
                prices,
                adapterProvider,
                quoteService,
                deliveryOptions,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "66053000");
    }

    private static String serializedItems() {
        return "[{\"skuId\":\"" + SKU + "\",\"quantity\":1}]";
    }

    private static ShippingQuote quote(UUID snapshotId, long snapshotVersion, String fingerprint) {
        return new ShippingQuote(
                UUID.randomUUID(),
                snapshotId,
                snapshotVersion,
                "66053000",
                fingerprint,
                "1",
                "PAC",
                1500,
                4,
                1,
                List.of(1),
                NOW,
                NOW.plusSeconds(300));
    }
}
