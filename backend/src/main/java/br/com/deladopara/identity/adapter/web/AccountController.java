package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.RegisterRequest;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.domain.Account;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;
    private final AccountRepository accounts;

    public AccountController(AccountService service, AccountRepository accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest req) {
        try {
            var account = service.register(req.email(), req.password(), Account.Role.CUSTOMER);
            var body = toResponse(account);
            return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                    .body(body);
        } catch (AccountService.DuplicateEmailException e) {
            throw new DuplicateEmailProblem();
        } catch (AccountService.InvalidInputException e) {
            throw new InvalidInputProblem(e.getCodigo(), e.getMessage());
        }
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name());
    }

    static class DuplicateEmailProblem extends RuntimeException {}

    static class InvalidInputProblem extends RuntimeException {
        private final String codigo;

        InvalidInputProblem(String codigo, String message) {
            super(message);
            this.codigo = codigo;
        }

        String getCodigo() {
            return codigo;
        }
    }
}
