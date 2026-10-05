package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.LoginRequest;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.application.CartLoginCoordinator;
import br.com.deladopara.identity.domain.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accounts;
    private final CartLoginCoordinator cartLogin;

    public SessionController(
            AuthenticationManager authenticationManager, AccountRepository accounts, CartLoginCoordinator cartLogin) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.cartLogin = cartLogin;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        var email = AccountService.normalize(req.email());
        Authentication auth =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        var existingSession = httpRequest.getSession(false);
        var previousSessionId = existingSession == null ? null : existingSession.getId();
        var account = accounts.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("not found"));
        if (existingSession != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        var session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        var mergeRequired = cartLogin.prepare(account.getId(), previousSessionId, session);
        SecurityContextHolder.setContext(context);
        return ResponseEntity.ok(toResponse(account, mergeRequired));
    }

    @GetMapping("/current")
    public ResponseEntity<AccountResponse> current(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var email = auth.getName();
        var account = accounts.findByEmailIgnoreCase(email).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toResponse(account, false));
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    private static AccountResponse toResponse(Account a, boolean mergeRequired) {
        return new AccountResponse(
                a.getId(), a.getEmail(), a.isEmailVerified(), a.getRole().name(), mergeRequired);
    }
}
