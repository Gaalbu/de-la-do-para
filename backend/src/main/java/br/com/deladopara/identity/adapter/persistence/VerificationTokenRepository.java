package br.com.deladopara.identity.adapter.persistence;

import br.com.deladopara.identity.domain.VerificationToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from VerificationToken token where token.tokenHash = :tokenHash")
    Optional<VerificationToken> findForUpdateByTokenHash(@Param("tokenHash") String tokenHash);
}
