package br.com.deladopara.eventing.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
                    SELECT * FROM event_outbox
                    WHERE status = 'PENDING'
                      AND available_at <= :now
                      AND (lease_until IS NULL OR lease_until <= :now)
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED
                    """, nativeQuery = true)
    List<OutboxEventEntity> findClaimable(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.status = br.com.deladopara.eventing.domain.OutboxEventStatus.PUBLISHED, "
            + "e.publishedAt = :publishedAt, e.leaseUntil = NULL "
            + "WHERE e.eventId = :eventId AND e.status = "
            + "br.com.deladopara.eventing.domain.OutboxEventStatus.PENDING "
            + "AND e.leaseUntil = :leaseUntil")
    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("publishedAt") Instant publishedAt);
}
