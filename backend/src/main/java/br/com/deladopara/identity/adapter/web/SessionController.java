package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import br.com.deladopara.identity.adapter.web.dto.AccountResponse;
import br.com.deladopara.identity.adapter.web.dto.LoginRequest;
import br.com.deladopara.identity.application.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

    public SessionController(AuthenticationManager authenticationManager, AccountRepository accounts) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        var email = AccountService.normalize(req.email());
        try {
            Authentication auth =
                    authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            var account =
                    accounts.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("not found"));
            var body = new AccountResponse(
                    account.getId(),
                    account.getEmail(),
                    account.isEmailVerified(),
                    account.getRole().name());
            return ResponseEntity.ok(body);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsProblem();
        }
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
        return ResponseEntity.ok(new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.isEmailVerified(),
                account.getRole().name()));
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

    static class BadCredentialsProblem extends RuntimeException {}
}
