package br.com.deladopara.catalog.adapter.persistence;

import br.com.deladopara.catalog.domain.Product;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends Repository<Product, UUID> {

    <S extends Product> S save(S product);

    <S extends Product> S saveAndFlush(S product);

    Optional<Product> findById(UUID id);

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findAll(Pageable pageable);

    @Query(value = """
            SELECT p.* FROM products p
            JOIN producers producer ON producer.id = p.producer_id
            WHERE p.active = TRUE AND producer.active = TRUE
              AND (:producerSlug IS NULL OR producer.slug = :producerSlug)
              AND (:category IS NULL OR p.category = :category)
              AND EXISTS (
                  SELECT 1 FROM product_skus sku
                  JOIN pricing_sku_prices price ON price.sku_id = sku.id
                  WHERE sku.product_id = p.id AND sku.active = TRUE
                    AND (:minPriceCents IS NULL OR price.unit_price_cents >= :minPriceCents)
                    AND (:maxPriceCents IS NULL OR price.unit_price_cents <= :maxPriceCents)
                    AND EXISTS (
                        SELECT 1 FROM inventory_lots lot
                        WHERE lot.sku_id = sku.id AND lot.blocked = FALSE
                          AND (lot.expires_on IS NULL OR lot.expires_on >= :availableOn)
                          AND lot.physical_units > lot.reserved_units
                    )
              )
            ORDER BY
              CASE WHEN :sort = 'PRICE_ASC' THEN (
                  SELECT MIN(price.unit_price_cents) FROM product_skus sku
                  JOIN pricing_sku_prices price ON price.sku_id = sku.id
                  WHERE sku.product_id = p.id AND sku.active = TRUE
              ) END ASC NULLS LAST,
              CASE WHEN :sort = 'PRICE_DESC' THEN (
                  SELECT MIN(price.unit_price_cents) FROM product_skus sku
                  JOIN pricing_sku_prices price ON price.sku_id = sku.id
                  WHERE sku.product_id = p.id AND sku.active = TRUE
              ) END DESC NULLS LAST,
              CASE WHEN :sort = 'NAME_ASC' THEN p.display_name END ASC,
              p.slug ASC
            """, countQuery = """
            SELECT COUNT(*) FROM products p
            JOIN producers producer ON producer.id = p.producer_id
            WHERE p.active = TRUE AND producer.active = TRUE
              AND (:producerSlug IS NULL OR producer.slug = :producerSlug)
              AND (:category IS NULL OR p.category = :category)
              AND EXISTS (
                  SELECT 1 FROM product_skus sku
                  JOIN pricing_sku_prices price ON price.sku_id = sku.id
                  WHERE sku.product_id = p.id AND sku.active = TRUE
                    AND (:minPriceCents IS NULL OR price.unit_price_cents >= :minPriceCents)
                    AND (:maxPriceCents IS NULL OR price.unit_price_cents <= :maxPriceCents)
                    AND EXISTS (
                        SELECT 1 FROM inventory_lots lot
                        WHERE lot.sku_id = sku.id AND lot.blocked = FALSE
                          AND (lot.expires_on IS NULL OR lot.expires_on >= :availableOn)
                          AND lot.physical_units > lot.reserved_units
                    )
              )
            """, nativeQuery = true)
    Page<Product> findAvailableForStorefront(
            @Param("producerSlug") String producerSlug,
            @Param("category") String category,
            @Param("minPriceCents") Long minPriceCents,
            @Param("maxPriceCents") Long maxPriceCents,
            @Param("availableOn") LocalDate availableOn,
            @Param("sort") String sort,
            Pageable pageable);
}
