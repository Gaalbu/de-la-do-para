package br.com.deladopara.catalog.adapter.persistence;

import br.com.deladopara.catalog.domain.ProductSku;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ProductSkuRepository extends Repository<ProductSku, UUID> {

    <S extends ProductSku> S save(S sku);

    <S extends ProductSku> S saveAndFlush(S sku);

    Optional<ProductSku> findById(UUID id);

    Optional<ProductSku> findBySkuCode(String skuCode);

    List<ProductSku> findAllByProductIdOrderBySkuCode(UUID productId);

    List<ProductSku> findAllByProductIdInOrderByProductIdAscSkuCodeAsc(List<UUID> productIds);

    boolean existsBySkuCode(String skuCode);
}
