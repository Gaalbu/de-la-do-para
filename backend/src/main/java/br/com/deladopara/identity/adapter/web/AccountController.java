package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.RegisterRequest;
import br.com.deladopara.identity.application.AccountService;
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

    private final AccountService service;
    private final IdentityRecoveryRateLimiter recoveryRateLimiter;

    public AccountController(AccountService service, IdentityRecoveryRateLimiter recoveryRateLimiter) {
        this.service = service;
        this.recoveryRateLimiter = recoveryRateLimiter;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest req) {
        var account = service.register(req.email(), req.password(), Account.Role.CUSTOMER);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
                .body(toResponse(account));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerificationResponse> verify(@jakarta.validation.Valid @RequestBody VerifyRequest request) {
        service.verifyEmail(request.token());
        return ResponseEntity.ok(new VerificationResponse(true));
    }

    @PostMapping("/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestRecovery(
            @jakarta.validation.Valid @RequestBody RecoveryRequest request, HttpServletRequest http) {
        recoveryRateLimiter.acquire(http.getRemoteAddr(), request.email());
        service.requestRecovery(request.email());
    }

    @PostMapping("/reset")
    public ResponseEntity<ResetResponse> reset(@jakarta.validation.Valid @RequestBody ResetRequest request) {
        service.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new ResetResponse(true));
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name());
    }

    public record VerifyRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128)
            String token) {}

    public record VerificationResponse(boolean emailVerified) {}

    public record RecoveryRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 254)
            String email) {}

    public record ResetRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128)
            String token,

            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72)
            String newPassword) {}

    public record ResetResponse(boolean passwordChanged) {}
}
