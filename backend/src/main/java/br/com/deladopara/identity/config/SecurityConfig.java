package br.com.deladopara.identity.config;

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
                                "/api/v1/sessions")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sessions/current")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sessions/current")
                        .authenticated()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(handler)
                        .ignoringRequestMatchers("/api/v1/sessions", "/api/v1/accounts/verify"))
                .sessionManagement(session -> session.sessionFixation(fix -> fix.changeSessionId()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, exc) -> {
                            res.setStatus(401);
                            res.setContentType("application/problem+json");
                            var body = "{\"title\":\"Não autenticado\",\"status\":401,"
                                    + "\"codigo\":\"IDENTITY_006\",\"detail\":\"Sessão ausente ou expirada\"}";
                            res.getWriter().write(body);
                        })
                        .accessDeniedHandler((req, res, exc) -> {
                            res.setStatus(403);
                            res.setContentType("application/problem+json");
                            var body = "{\"title\":\"Acesso negado\",\"status\":403,"
                                    + "\"codigo\":\"IDENTITY_008\",\"detail\":\"Permissão insuficiente ou CSRF ausente\"}";
                            res.getWriter().write(body);
                        }))
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
