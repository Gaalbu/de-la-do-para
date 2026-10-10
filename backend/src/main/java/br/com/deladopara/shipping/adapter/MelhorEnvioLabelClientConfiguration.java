package br.com.deladopara.shipping.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "shipping.melhor-envio", name = "enabled", havingValue = "true")
public class MelhorEnvioLabelClientConfiguration {

    @Bean
    MelhorEnvioLabelClient melhorEnvioLabelClient(
            ObjectMapper objectMapper,
            @Value("${shipping.melhor-envio.credential:}") String credential,
            @Value("${shipping.melhor-envio.user-agent:}") String userAgent) {
        var httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return new MelhorEnvioLabelClient(
                RestClient.builder().requestFactory(requestFactory), objectMapper, credential, userAgent);
    }
}
