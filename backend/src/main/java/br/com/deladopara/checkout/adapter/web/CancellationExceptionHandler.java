package br.com.deladopara.checkout.adapter.web;

import br.com.deladopara.checkout.application.CheckoutCancellationService.OrderNotCancellableException;
import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.application.OrderQueryService.OrderNotVisibleException;
import br.com.deladopara.orders.application.OrderService.OrderNotFoundException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CancellationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CancellationExceptionHandler {

    @ExceptionHandler({InvalidOrderTokenException.class})
    ResponseEntity<Problem> unauthorized() {
        return problem(HttpStatus.UNAUTHORIZED, "ORDER_003", "prova de acesso ao pedido ausente ou inválida");
    }

    @ExceptionHandler({OrderNotVisibleException.class, OrderNotFoundException.class})
    ResponseEntity<Problem> notFound() {
        return problem(HttpStatus.NOT_FOUND, "ORDER_001", "pedido não encontrado");
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    ResponseEntity<Problem> notCancellable() {
        return problem(HttpStatus.CONFLICT, "ORDER_002", "ação incompatível com modalidade ou estado do pedido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Problem> malformed() {
        return problem(HttpStatus.BAD_REQUEST, "ORDER_004", "parâmetros inválidos");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        var body = new Problem("Erro", status.value(), detail, code, MDC.get("correlationId"));
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                .body(body);
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
