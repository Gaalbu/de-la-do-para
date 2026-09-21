package br.com.deladopara.inventory.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface InventoryLotRepository extends Repository<InventoryLotEntity, UUID> {

    <S extends InventoryLotEntity> S save(S lot);

    java.util.Optional<InventoryLotEntity> findById(UUID id);

    List<InventoryLotEntity> findAllBySkuIdOrderByReceivedAtAscIdAsc(UUID skuId);
}
