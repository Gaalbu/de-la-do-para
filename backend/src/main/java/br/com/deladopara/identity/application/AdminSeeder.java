package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.domain.Account;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    ApplicationRunner seedAdmin(AccountRepository accounts, PasswordEncoder encoder, Clock clock) {
        return args -> {
            var existingAdmin = accounts.findByEmailIgnoreCase("admin@deladopara.local");
            if (existingAdmin.isPresent()) {
                return;
            }
            var hasAnyAdmin = accounts.findAll().stream().anyMatch(a -> a.getRole() == Account.Role.ADMIN);
            if (hasAnyAdmin) {
                return;
            }
            var password = UUID.randomUUID().toString().substring(0, 12) + "A1!";
            var admin = new Account(
                    UUID.randomUUID(),
                    "admin@deladopara.local",
                    encoder.encode(password),
                    Account.Role.ADMIN,
                    clock.instant());
            accounts.save(admin);
            LOG.info("Admin inicial criado: admin@deladopara.local / senha: {}", password);
        };
    }
}
