package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.PasswordResetRequest;
import br.com.deladopara.identity.adapter.web.dto.RecoveryRequest;
import br.com.deladopara.identity.adapter.web.dto.RegisterRequest;
import br.com.deladopara.identity.adapter.web.dto.VerifyEmailRequest;
import br.com.deladopara.identity.application.AccountRecoveryService;
import br.com.deladopara.identity.application.AccountVerificationService;
import br.com.deladopara.identity.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountVerificationService verification;
    private final AccountRecoveryService recovery;

    public AccountController(AccountVerificationService verification, AccountRecoveryService recovery) {
        this.verification = verification;
        this.recovery = recovery;
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

    @PostMapping("/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestRecovery(@Valid @RequestBody RecoveryRequest request, HttpServletRequest servletRequest) {
        recovery.request(request.email(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/reset")
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        recovery.reset(request.token(), request.newPassword());
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name());
    }
}
