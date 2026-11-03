package br.com.deladopara.shipping.adapter;

import br.com.deladopara.shipping.application.FreightQuoteAdapter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "shipping.adapter.enabled", havingValue = "true")
public class MelhorEnvioSandboxConfiguration {

    @Bean
    ShippingAdapterProperties shippingAdapterProperties(
            @Value("${shipping.adapter.base-url:https://sandbox.melhorenvio.com.br}") String baseUrl,
            @Value("${shipping.adapter.credential}") String credential,
            @Value("${shipping.adapter.timeout:PT5S}") Duration timeout,
            @Value("${shipping.adapter.quote-validity:PT5M}") Duration quoteValidity,
            @Value("${shipping.adapter.origin-postal-code:66053000}") String originPostalCode,
            @Value("${shipping.adapter.user-agent}") String userAgent) {
        return new ShippingAdapterProperties(baseUrl, credential, timeout, quoteValidity, originPostalCode, userAgent);
    }

    @Bean
    FreightQuoteAdapter melhorEnvioSandboxAdapter(
            RestClient.Builder builder,
            ShippingAdapterProperties properties,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        var httpClient =
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new MelhorEnvioSandboxAdapter(builder.requestFactory(requestFactory), mapper, properties);
    }
}
