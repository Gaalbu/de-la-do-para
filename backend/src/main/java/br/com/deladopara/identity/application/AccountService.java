package br.com.deladopara.identity.application;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.domain.Account;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountService(AccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Account register(String rawEmail, String rawPassword, Account.Role role) {
        var email = normalize(rawEmail);
        validateEmail(email);
        validatePassword(rawPassword);
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }
        var now = clock.instant();
        var account = new Account(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), role, now);
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException();
        }
    }

    public Optional<UUID> accountIdByEmail(String email) {
        return accounts.findByEmailIgnoreCase(normalize(email)).map(Account::getId);
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254 || !email.contains("@")) {
            throw new InvalidInputException("IDENTITY_001", "e-mail inválido");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new InvalidInputException("IDENTITY_001", "senha deve ter 8..72 caracteres");
        }
    }

    public static class DuplicateEmailException extends RuntimeException {}

    public static class InvalidInputException extends RuntimeException {
        private final String codigo;

        public InvalidInputException(String codigo, String message) {
            super(message);
            this.codigo = codigo;
        }

        public String getCodigo() {
            return codigo;
        }
    }
}
