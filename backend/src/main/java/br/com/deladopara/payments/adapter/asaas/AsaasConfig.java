package br.com.deladopara.payments.adapter.asaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Active only with {@code payments.provider=asaas}; local runs keep the explicit simulator. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "payments.provider", havingValue = "asaas")
@EnableConfigurationProperties(AsaasProperties.class)
public class AsaasConfig {

    @Bean
    AsaasPaymentProvider asaasPaymentProvider(AsaasProperties properties, Clock clock) {
        var requests = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requests.setReadTimeout(properties.timeout());
        return new AsaasPaymentProvider(
                RestClient.builder().requestFactory(requests), properties, new ObjectMapper(), clock);
    }
}
