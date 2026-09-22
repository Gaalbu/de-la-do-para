package br.com.deladopara.eventing.application;

import br.com.deladopara.eventing.domain.OutboxEvent;
import java.time.Instant;

public record ClaimedOutboxEvent(OutboxEvent event, Instant leaseUntil) {}
