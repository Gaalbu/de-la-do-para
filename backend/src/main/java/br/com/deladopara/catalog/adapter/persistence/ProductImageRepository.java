package br.com.deladopara.catalog.adapter.persistence;

import br.com.deladopara.catalog.domain.ProductImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    Optional<ProductImage> findByProduct_Id(UUID productId);

    java.util.List<ProductImage> findAllByProduct_IdIn(java.util.List<UUID> productIds);

    Optional<ProductImage> findByStorageKey(UUID storageKey);

    void delete(ProductImage image);
}
