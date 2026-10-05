package br.com.deladopara.identity.adapter.persistence;

import br.com.deladopara.identity.domain.VerificationToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VerificationToken t where t.tokenHash = :tokenHash")
    Optional<VerificationToken> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VerificationToken t where t.id = :id")
    Optional<VerificationToken> findLockedById(@Param("id") UUID id);

    List<VerificationToken> findAllByAccountIdAndTypeAndUsedAtIsNull(UUID accountId, VerificationToken.TokenType type);

    @Modifying
    @Query("update VerificationToken t set t.usedAt = :now where t.accountId = :accountId "
            + "and t.type = :type and t.usedAt is null and t.id <> :exceptId")
    int consumeOthers(
            @Param("accountId") UUID accountId,
            @Param("type") VerificationToken.TokenType type,
            @Param("exceptId") UUID exceptId,
            @Param("now") java.time.Instant now);
}
