package br.com.deladopara.shipping.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShippingJacksonConfig {

    @Bean
    ObjectMapper shippingObjectMapper() {
        return new ObjectMapper();
    }
}
