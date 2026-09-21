package br.com.deladopara.inventory.adapter.web;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class InventoryExceptionHandler {

    @ExceptionHandler(OptimisticLockException.class)
    ResponseEntity<Problem> conflict(OptimisticLockException exception) {
        return problem(HttpStatus.CONFLICT, "INVENTORY_003", "versão do lote está desatualizada");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Problem> invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVENTORY_001", exception.getMessage());
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new Problem("Erro", status.value(), detail, code, MDC.get("correlationId")));
    }

    record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
