package br.com.deladopara.catalog.application;

import br.com.deladopara.catalog.domain.Product;

public record StorefrontQuery(
        String producerSlug,
        Product.Category category,
        Long minPriceCents,
        Long maxPriceCents,
        Sort sort,
        int page,
        int size) {

    public StorefrontQuery {
        producerSlug = producerSlug == null || producerSlug.isBlank()
                ? null
                : producerSlug.trim().toLowerCase();
        sort = sort == null ? Sort.RELEVANCE : sort;
        if (minPriceCents != null && minPriceCents < 0
                || maxPriceCents != null && maxPriceCents < 0
                || minPriceCents != null && maxPriceCents != null && minPriceCents > maxPriceCents) {
            throw new ProductService.InvalidProductInputException("faixa de preço inválida");
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new ProductService.InvalidProductInputException("página deve ser >= 0 e tamanho entre 1 e 50");
        }
    }

    public enum Sort {
        RELEVANCE,
        PRICE_ASC,
        PRICE_DESC,
        NAME_ASC
    }
}
