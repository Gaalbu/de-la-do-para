package br.com.deladopara.eventing.application;

public interface EventHandler {

    String handlerName();

    String eventType();

    int schemaVersion();

    void handle(EventEnvelope event);
}
