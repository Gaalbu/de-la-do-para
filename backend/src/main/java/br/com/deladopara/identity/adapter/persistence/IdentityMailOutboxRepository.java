package br.com.deladopara.identity.adapter.persistence;

import br.com.deladopara.identity.domain.IdentityMailOutbox;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentityMailOutboxRepository extends JpaRepository<IdentityMailOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from IdentityMailOutbox m where m.status = 'PENDING' and m.availableAt <= CURRENT_TIMESTAMP "
            + "and (m.leaseUntil is null or m.leaseUntil <= CURRENT_TIMESTAMP) order by m.createdAt")
    List<IdentityMailOutbox> lockDue(Pageable pageable);

    @Modifying
    @Query("delete from IdentityMailOutbox m where m.tokenId in :tokenIds and m.status = 'PENDING'")
    int deletePendingForTokens(@Param("tokenIds") Collection<UUID> tokenIds);
}
