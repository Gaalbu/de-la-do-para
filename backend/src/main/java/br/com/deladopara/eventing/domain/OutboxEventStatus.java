package br.com.deladopara.eventing.domain;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    QUARANTINED
}
