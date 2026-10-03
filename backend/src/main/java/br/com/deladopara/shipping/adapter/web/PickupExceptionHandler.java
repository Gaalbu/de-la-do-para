package br.com.deladopara.shipping.adapter.web;

import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.application.OrderQueryService.OrderNotVisibleException;
import br.com.deladopara.orders.application.OrderService.OrderNotFoundException;
import br.com.deladopara.shipping.application.PickupService.InvalidPickupCodeException;
import br.com.deladopara.shipping.application.PickupService.PickupUnavailableException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "br.com.deladopara.shipping.adapter.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PickupExceptionHandler {

    @ExceptionHandler({InvalidOrderTokenException.class})
    ResponseEntity<Problem> unauthorized() {
        return problem(HttpStatus.UNAUTHORIZED, "ORDER_003", "prova de acesso ao pedido ausente ou inválida");
    }

    @ExceptionHandler({OrderNotVisibleException.class, OrderNotFoundException.class})
    ResponseEntity<Problem> notFound() {
        return problem(HttpStatus.NOT_FOUND, "ORDER_001", "pedido não encontrado");
    }

    @ExceptionHandler(PickupUnavailableException.class)
    ResponseEntity<Problem> unavailable() {
        return problem(HttpStatus.CONFLICT, "SHIPPING_006", "retirada indisponível para o estado atual do pedido");
    }

    @ExceptionHandler(InvalidPickupCodeException.class)
    ResponseEntity<Problem> invalidCode() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "SHIPPING_007", "código de retirada inválido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Problem> malformed() {
        return problem(HttpStatus.BAD_REQUEST, "SHIPPING_001", "identificador de pedido inválido");
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
