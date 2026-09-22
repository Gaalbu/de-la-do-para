package br.com.deladopara.pricing.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface SkuPriceRepository extends Repository<SkuPriceEntity, UUID> {

    <S extends SkuPriceEntity> S save(S price);

    java.util.Optional<SkuPriceEntity> findById(UUID skuId);

    List<SkuPriceEntity> findAllBySkuIdIn(List<UUID> skuIds);
}
