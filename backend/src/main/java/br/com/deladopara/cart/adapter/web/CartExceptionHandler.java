package br.com.deladopara.cart.adapter.web;

import br.com.deladopara.cart.application.CartConflictException;
import br.com.deladopara.cart.application.CartInvalidInputException;
import br.com.deladopara.cart.application.CartNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CartExceptionHandler {

    @ExceptionHandler({CartConflictException.class, OptimisticLockException.class})
    ResponseEntity<Problem> conflict() {
        return problem(HttpStatus.CONFLICT, "CART_002", "carrinho está desatualizado ou não está aberto");
    }

    @ExceptionHandler(CartNotFoundException.class)
    ResponseEntity<Problem> notFound() {
        return problem(HttpStatus.NOT_FOUND, "CART_003", "carrinho não encontrado");
    }

    @ExceptionHandler({CartInvalidInputException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Problem> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "CART_001", "dados do carrinho inválidos");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new Problem("Erro", status.value(), detail, code, MDC.get("correlationId")));
    }

    record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
