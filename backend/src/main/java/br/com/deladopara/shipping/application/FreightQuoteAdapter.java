package br.com.deladopara.shipping.application;

import java.time.Instant;

public interface FreightQuoteAdapter {

    CarrierQuote quote(ShippingQuoteRequest request, String sandboxPayload, Instant now);
}
