package br.com.deladopara.shipping.application;

import java.time.Instant;
import java.util.List;

public interface FreightQuoteAdapter {

    List<CarrierQuote> quote(ShippingQuoteRequest request, Instant now);
}
