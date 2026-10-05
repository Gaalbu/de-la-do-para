package br.com.deladopara.cart.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    Optional<CartEntity> findByGuestSessionKey(String guestSessionKey);

    Optional<CartEntity> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct c from CartEntity c left join fetch c.items where c.accountId = :accountId")
    Optional<CartEntity> findLockedByAccountId(@Param("accountId") UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct c from CartEntity c left join fetch c.items where c.guestSessionKey = :guestSessionKey")
    Optional<CartEntity> findLockedByGuestSessionKey(@Param("guestSessionKey") String guestSessionKey);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update CartEntity c set c.guestSessionKey = null, c.accountId = :accountId "
                    + "where c.id = :cartId and c.guestSessionKey = :guestKey")
    int transferGuestCartToAccount(
            @Param("cartId") UUID cartId, @Param("guestKey") String guestKey, @Param("accountId") UUID accountId);
}
