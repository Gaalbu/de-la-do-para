package br.com.deladopara.identity.config;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);

        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/error",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/api/v1/status",
                                "/api/v1/csrf",
                                "/api/v1/accounts",
                                "/api/v1/accounts/verify",
                                "/api/v1/accounts/recovery",
                                "/api/v1/accounts/reset",
                                "/api/v1/sessions",
                                "/api/v1/cart/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/current")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/current")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/product-images/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(handler)
                        .ignoringRequestMatchers("/api/v1/sessions", "/api/v1/accounts/verify"))
                .sessionManagement(session -> session.sessionFixation(fix -> fix.changeSessionId()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, exc) ->
                                writeProblem(res, 401, "IDENTITY_006", "Não autenticado", "Sessão ausente ou expirada"))
                        .accessDeniedHandler((req, res, exc) -> writeProblem(
                                res, 403, "IDENTITY_008", "Acesso negado", "Permissão insuficiente ou CSRF ausente")))
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    private void writeProblem(
            jakarta.servlet.http.HttpServletResponse response, int status, String code, String title, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        var correlationId = MDC.get("correlationId");
        response.getWriter()
                .printf(
                        "{\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"codigo\":\"%s\","
                                + "\"correlationId\":\"%s\"}",
                        title, status, detail, code, correlationId == null ? UUID.randomUUID() : correlationId);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
