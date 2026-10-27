package br.com.deladopara.eventing.adapter.web;

import br.com.deladopara.eventing.application.EventReplayService.InvalidReplayReasonException;
import br.com.deladopara.eventing.application.EventReplayService.QuarantinedRecordNotFoundException;
import br.com.deladopara.eventing.application.EventReplayService.ReplayNotEligibleException;
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

@RestControllerAdvice(assignableTypes = AdminEventReplayController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminEventReplayExceptionHandler {

    @ExceptionHandler(QuarantinedRecordNotFoundException.class)
    ResponseEntity<Problem> notFound() {
        return problem(HttpStatus.NOT_FOUND, "EVENTING_001", "registro em quarentena não encontrado");
    }

    @ExceptionHandler(ReplayNotEligibleException.class)
    ResponseEntity<Problem> notEligible() {
        return problem(
                HttpStatus.CONFLICT,
                "EVENTING_002",
                "registro sem evento legível ou evento original fora da outbox; replay não é possível");
    }

    @ExceptionHandler({
        InvalidReplayReasonException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<Problem> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "EVENTING_003", "identificador ou motivo (1 a 200 caracteres) inválido");
    }

    private static ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new Problem("Erro", status.value(), detail, code, MDC.get("correlationId")));
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
