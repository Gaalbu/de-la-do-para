package br.com.deladopara.pricing.adapter.web;

import br.com.deladopara.pricing.application.CouponAdminService.CouponCodeConflictException;
import br.com.deladopara.pricing.application.CouponAdminService.CouponLimitBelowUsageException;
import br.com.deladopara.pricing.application.CouponAdminService.CouponNotFoundException;
import br.com.deladopara.pricing.application.CouponAdminService.InvalidCouponInputException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "br.com.deladopara.pricing.adapter.web")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PricingExceptionHandler {

    @ExceptionHandler(InvalidCouponInputException.class)
    ResponseEntity<Problem> invalid(InvalidCouponInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, "PRICING_001", exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<Problem> malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "PRICING_001", "dados inválidos");
    }

    @ExceptionHandler(CouponCodeConflictException.class)
    ResponseEntity<Problem> conflict(CouponCodeConflictException exception) {
        return problem(HttpStatus.CONFLICT, "PRICING_002", "código de cupom já cadastrado");
    }

    @ExceptionHandler(CouponNotFoundException.class)
    ResponseEntity<Problem> notFound(CouponNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "PRICING_003", "cupom não encontrado");
    }

    @ExceptionHandler(CouponLimitBelowUsageException.class)
    ResponseEntity<Problem> limitBelowUsage(CouponLimitBelowUsageException exception) {
        return problem(HttpStatus.CONFLICT, "PRICING_004", "limite global não pode ficar abaixo do uso registrado");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        var body = new Problem("Erro", status.value(), detail, code, MDC.get("correlationId"));
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(body);
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
