package br.com.deladopara.cart.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    Optional<CartEntity> findByGuestSessionKey(String guestSessionKey);

    Optional<CartEntity> findByAccountId(UUID accountId);
}
