package br.com.deladopara.eventing.application;

public class UnsupportedEventException extends RuntimeException {

    public UnsupportedEventException(String eventType, int schemaVersion) {
        super("No event handler supports type " + eventType + " and schema version " + schemaVersion);
    }
}
