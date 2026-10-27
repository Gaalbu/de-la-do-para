package br.com.deladopara.identity.adapter.persistence;

import br.com.deladopara.identity.domain.VerificationToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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
    @Query("select token from VerificationToken token where token.tokenHash = :tokenHash")
    Optional<VerificationToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);

    @Query("select token.accountId from VerificationToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findAccountIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update VerificationToken token
            set token.usedAt = :now
            where token.tokenHash = :tokenHash
              and token.accountId = :accountId
              and token.type = :type
              and token.usedAt is null
              and token.expiresAt > :now
            """)
    int consumeIfActive(
            @Param("tokenHash") String tokenHash,
            @Param("accountId") UUID accountId,
            @Param("type") VerificationToken.TokenType type,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update VerificationToken token
            set token.usedAt = :now
            where token.accountId = :accountId
              and token.type = :type
              and token.usedAt is null
            """)
    int markAllUnusedByAccountAndType(
            @Param("accountId") UUID accountId,
            @Param("type") VerificationToken.TokenType type,
            @Param("now") Instant now);
}
