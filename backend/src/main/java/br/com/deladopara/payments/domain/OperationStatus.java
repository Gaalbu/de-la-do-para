package br.com.deladopara.payments.domain;

/** State of one external call. IN_FLIGHT means the request may already have reached the provider. */
public enum OperationStatus {
    PENDING,
    IN_FLIGHT,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}
