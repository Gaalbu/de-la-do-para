package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.RegisterRequest;
import br.com.deladopara.identity.adapter.web.dto.VerifyEmailRequest;
import br.com.deladopara.identity.application.AccountVerificationService;
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

    private final AccountVerificationService verification;

    public AccountController(AccountVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest req) {
        var account = verification.register(req.email(), req.password());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(toResponse(account));
    }

    @PostMapping("/verify")
    public AccountResponse verify(@Valid @RequestBody VerifyEmailRequest request) {
        return toResponse(verification.verify(request.token()));
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name());
    }
}
