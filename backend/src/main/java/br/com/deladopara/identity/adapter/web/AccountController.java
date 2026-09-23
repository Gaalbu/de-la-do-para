package br.com.deladopara.identity.adapter.web;

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

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest req) {
        var account = service.register(req.email(), req.password(), Account.Role.CUSTOMER);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(toResponse(account));
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name());
    }
}
