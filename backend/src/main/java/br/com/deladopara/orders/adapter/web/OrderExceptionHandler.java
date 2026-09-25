package br.com.deladopara.orders.adapter.web;

import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.application.OrderQueryService.InvalidPageException;
import br.com.deladopara.orders.application.OrderQueryService.OrderNotVisibleException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "br.com.deladopara.orders.adapter.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderExceptionHandler {

    @ExceptionHandler(OrderNotVisibleException.class)
    ResponseEntity<Problem> notVisible() {
        return problem(HttpStatus.NOT_FOUND, "ORDER_001", "pedido não encontrado");
    }

    @ExceptionHandler(InvalidOrderTokenException.class)
    ResponseEntity<Problem> invalidToken() {
        return problem(HttpStatus.UNAUTHORIZED, "ORDER_003", "prova de acesso ao pedido ausente ou inválida");
    }

    @ExceptionHandler({InvalidPageException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Problem> malformed() {
        return problem(HttpStatus.BAD_REQUEST, "ORDER_004", "parâmetros inválidos");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        var body = new Problem("Erro", status.value(), detail, code, MDC.get("correlationId"));
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .header("Cache-Control", "private, no-store")
                .body(body);
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
