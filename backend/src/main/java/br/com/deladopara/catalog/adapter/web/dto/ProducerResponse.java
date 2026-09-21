package br.com.deladopara.catalog.adapter.web.dto;

import br.com.deladopara.catalog.domain.Producer;
import java.time.Instant;
import java.util.UUID;

public record ProducerResponse(
        UUID id,
        String slug,
        String displayName,
        String originLabel,
        String description,
        boolean demonstration,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProducerResponse from(Producer producer) {
        return new ProducerResponse(
                producer.getId(),
                producer.getSlug(),
                producer.getDisplayName(),
                producer.getOriginLabel(),
                producer.getDescription(),
                true,
                producer.isActive(),
                producer.getCreatedAt(),
                producer.getUpdatedAt());
    }
}
