package br.com.deladopara.payments.adapter.web;

import br.com.deladopara.payments.application.AdminPaymentLookups.InvalidLookupReasonException;
import br.com.deladopara.payments.application.AdminPaymentLookups.LookupNotEligibleException;
import br.com.deladopara.payments.application.PaymentIntentService.PaymentIntentNotFoundException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AdminPaymentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminPaymentExceptionHandler {

    @ExceptionHandler(PaymentIntentNotFoundException.class)
    ResponseEntity<Problem> notFound() {
        return problem(HttpStatus.NOT_FOUND, "PAYMENT_020", "pagamento não encontrado");
    }

    @ExceptionHandler(LookupNotEligibleException.class)
    ResponseEntity<Problem> notEligible() {
        return problem(
                HttpStatus.CONFLICT, "PAYMENT_021", "pagamento não está em conciliação, análise ou reembolso pendente");
    }

    @ExceptionHandler({
        InvalidLookupReasonException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<Problem> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "PAYMENT_022", "identificador ou motivo (1 a 200 caracteres) inválido");
    }

    private static ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new Problem("Erro", status.value(), detail, code, MDC.get("correlationId")));
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
