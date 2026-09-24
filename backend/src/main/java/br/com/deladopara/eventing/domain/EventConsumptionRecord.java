package br.com.deladopara.eventing.domain;

import br.com.deladopara.eventing.application.EventEnvelope;
import java.time.Instant;

public record EventConsumptionRecord(
        EventEnvelope envelope, EventConsumptionResult result, Instant receivedAt, Instant appliedAt) {}
