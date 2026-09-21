package br.com.deladopara.catalog.adapter.web.dto;

import java.util.List;

public record ProductPageResponse(
        List<ProductResponse> content, int page, int size, long totalElements, int totalPages) {}
