package br.com.deladopara.eventing.application;

public class InvalidEventEnvelopeException extends RuntimeException {

    public InvalidEventEnvelopeException(String message) {
        super(message);
    }

    public InvalidEventEnvelopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
