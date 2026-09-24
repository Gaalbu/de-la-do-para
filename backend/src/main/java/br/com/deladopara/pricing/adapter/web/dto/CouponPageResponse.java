package br.com.deladopara.pricing.adapter.web.dto;

import java.util.List;

public record CouponPageResponse(
        List<CouponResponse> content, int page, int size, long totalElements, int totalPages) {}
