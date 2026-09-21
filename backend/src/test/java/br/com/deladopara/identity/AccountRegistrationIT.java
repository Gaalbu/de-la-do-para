package br.com.deladopara.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.domain.Account;
import br.com.deladopara.support.PostgresTestContainer;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestContainer.class)
class AccountRegistrationIT {
    private final AccountService accounts;

    @Autowired
    AccountRegistrationIT(AccountService accounts) {
        this.accounts = accounts;
    }

    @Test
    void concurrentRegistrationsOfTheSameEmailCreateOneAccountAndReportDuplicates() throws Exception {
        var email = "race-" + UUID.randomUUID() + "@example.com";
        var attempts = 8;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(attempts)) {
            var results = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var i = 0; i < attempts; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        accounts.register(email, "correct-horse-battery", Account.Role.CUSTOMER);
                        return true;
                    } catch (AccountService.DuplicateEmailException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            var created = 0;
            for (var result : results) {
                if (result.get()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        }
    }
}
