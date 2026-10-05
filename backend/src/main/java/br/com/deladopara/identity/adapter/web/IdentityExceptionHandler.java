package br.com.deladopara.identity.adapter.web;

import br.com.deladopara.identity.application.AccountRecoveryService;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.identity.application.AccountVerificationService;
import br.com.deladopara.identity.application.RecoveryRateLimitException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AccountService.DuplicateEmailException.class)
    ResponseEntity<Problem> duplicate() {
        return problem(HttpStatus.CONFLICT, "IDENTITY_002", "e-mail já cadastrado");
    }

    @ExceptionHandler(AccountService.InvalidInputException.class)
    ResponseEntity<Problem> invalid(AccountService.InvalidInputException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Problem> badCredentials() {
        return problem(HttpStatus.UNAUTHORIZED, "IDENTITY_005", "credenciais inválidas");
    }

    @ExceptionHandler(AccountVerificationService.InvalidVerificationTokenException.class)
    ResponseEntity<Problem> invalidVerificationToken() {
        return problem(HttpStatus.GONE, "IDENTITY_004", "token inválido, expirado ou já utilizado");
    }

    @ExceptionHandler(AccountRecoveryService.InvalidRecoveryTokenException.class)
    ResponseEntity<Problem> invalidRecoveryToken() {
        return problem(HttpStatus.GONE, "IDENTITY_013", "token inválido, expirado ou já utilizado");
    }

    @ExceptionHandler(RecoveryRateLimitException.class)
    ResponseEntity<Problem> recoveryRateLimit(RecoveryRateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Integer.toString(ex.getRetryAfterSeconds()))
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new Problem(
                        "Erro", 429, "limite de recuperação excedido", "IDENTITY_012", MDC.get("correlationId")));
    }

    @ExceptionHandler(MailException.class)
    ResponseEntity<Problem> mailUnavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_009", "não foi possível enviar a verificação agora");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation() {
        return problem(HttpStatus.BAD_REQUEST, "IDENTITY_001", "dados inválidos");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String codigo, String detail) {
        var correlationId = MDC.get("correlationId");
        var body = new Problem("Erro", status.value(), detail, codigo, correlationId);
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(body);
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
