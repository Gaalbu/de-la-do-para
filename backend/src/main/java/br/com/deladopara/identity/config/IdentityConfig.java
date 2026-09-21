package br.com.deladopara.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfig {

    @Bean
    PasswordEncoder passwordEncoder(IdentityProperties props) {
        int strength = props.bcryptStrength();
        if (strength < 4 || strength > 31) {
            strength = 12;
        }
        return new BCryptPasswordEncoder(strength);
    }
}
