package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.application.ProducerService.InvalidProducerInputException;
import br.com.deladopara.catalog.application.ProducerService.ProducerNotFoundException;
import br.com.deladopara.catalog.application.ProducerService.ProducerSlugConflictException;
import br.com.deladopara.catalog.application.ProductService.InvalidProductInputException;
import br.com.deladopara.catalog.application.ProductService.ProductConflictException;
import br.com.deladopara.catalog.application.ProductService.ProductNotFoundException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(ProducerSlugConflictException.class)
    ResponseEntity<Problem> conflict(ProducerSlugConflictException exception) {
        return problem(HttpStatus.CONFLICT, "CATALOG_002", "identificador já cadastrado");
    }

    @ExceptionHandler(ProductConflictException.class)
    ResponseEntity<Problem> productConflict(ProductConflictException exception) {
        return problem(HttpStatus.CONFLICT, "CATALOG_002", "identificador já cadastrado");
    }

    @ExceptionHandler(ProducerNotFoundException.class)
    ResponseEntity<Problem> notFound(ProducerNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "CATALOG_003", "produtor não encontrado");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<Problem> productNotFound(ProductNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "CATALOG_003", "produto não encontrado");
    }

    @ExceptionHandler(InvalidProducerInputException.class)
    ResponseEntity<Problem> invalid(InvalidProducerInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(InvalidProductInputException.class)
    ResponseEntity<Problem> invalidProduct(InvalidProductInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, "CATALOG_001", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException exception) {
        return problem(HttpStatus.BAD_REQUEST, "CATALOG_001", "dados inválidos");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Problem> malformedRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "CATALOG_001", "parâmetro ou corpo inválido");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        var body = new Problem("Erro", status.value(), detail, code, MDC.get("correlationId"));
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(body);
    }

    public record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
