package br.com.deladopara.catalog.adapter.web.dto;

import java.util.List;

public record StorefrontProductPageResponse(
        List<StorefrontProductResponse> content, int page, int size, long totalElements, int totalPages) {}
