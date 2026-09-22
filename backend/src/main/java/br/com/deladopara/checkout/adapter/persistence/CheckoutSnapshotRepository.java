package br.com.deladopara.checkout.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutSnapshotRepository extends JpaRepository<CheckoutSnapshotEntity, UUID> {

    Optional<CheckoutSnapshotEntity> findByIdAndGuestSessionKey(UUID id, String guestSessionKey);
}
