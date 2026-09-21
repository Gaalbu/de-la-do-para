package br.com.deladopara.inventory.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface InventoryMovementRepository extends Repository<InventoryMovementEntity, UUID> {

    <S extends InventoryMovementEntity> S save(S movement);

    Optional<InventoryMovementEntity> findByIdempotencyKey(String idempotencyKey);
}
