package br.com.deladopara.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.domain.Account;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminSeederTest {
    private static final Pattern PASSWORD_LOG = Pattern.compile("senha: [a-f0-9]+");

    private final Logger logger = (Logger) LoggerFactory.getLogger(AdminSeeder.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(events);
        events.stop();
    }

    @Test
    void doesNotWriteGeneratedAdminPasswordToLogs() throws Exception {
        var accounts = mock(AccountRepository.class);
        when(accounts.existsByRole(Account.Role.ADMIN)).thenReturn(false);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("encoded-password");
        var clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);

        new AdminSeeder().seedAdmin(accounts, encoder, clock).run(new DefaultApplicationArguments());

        assertThat(events.list)
                .extracting(event ->
                        PASSWORD_LOG.matcher(event.getFormattedMessage()).replaceAll("senha: [REDACTED]"))
                .containsExactly("Admin inicial criado: admin@deladopara.local")
                .noneMatch(message -> message.contains("senha:"));
    }
}
