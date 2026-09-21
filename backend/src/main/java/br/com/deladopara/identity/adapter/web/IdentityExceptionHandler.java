package br.com.deladopara.identity.adapter.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AccountController.DuplicateEmailProblem.class)
    ResponseEntity<Problem> duplicate(AccountController.DuplicateEmailProblem ex) {
        return problem(HttpStatus.CONFLICT, "IDENTITY_002", "e-mail já cadastrado");
    }

    @ExceptionHandler(AccountController.InvalidInputProblem.class)
    ResponseEntity<Problem> invalid(AccountController.InvalidInputProblem ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(SessionController.BadCredentialsProblem.class)
    ResponseEntity<Problem> badCreds(SessionController.BadCredentialsProblem ex) {
        return problem(HttpStatus.UNAUTHORIZED, "IDENTITY_005", "credenciais inválidas");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException ex) {
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
