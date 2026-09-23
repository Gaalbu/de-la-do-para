package br.com.deladopara.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityConfig {

    @Bean
    PasswordEncoder passwordEncoder(IdentityProperties props) {
        return new BCryptPasswordEncoder(props.bcryptStrength());
    }

    @Bean
    CookieSerializer cookieSerializer() {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("DLSESSION");
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge(43200);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
