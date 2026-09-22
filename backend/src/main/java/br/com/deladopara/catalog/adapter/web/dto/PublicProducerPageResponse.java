package br.com.deladopara.catalog.adapter.web.dto;

import java.util.List;

public record PublicProducerPageResponse(
        String slug,
        String displayName,
        String originLabel,
        String description,
        List<StorefrontProductResponse> products,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
