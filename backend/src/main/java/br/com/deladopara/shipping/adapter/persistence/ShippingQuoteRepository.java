package br.com.deladopara.shipping.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingQuoteRepository extends JpaRepository<ShippingQuoteEntity, UUID> {

    Optional<ShippingQuoteEntity> findByIdAndSnapshotIdAndSnapshotVersion(
            UUID id, UUID snapshotId, long snapshotVersion);

    List<ShippingQuoteEntity> findAllBySnapshotIdAndSnapshotVersionAndDestinationPostalCodeAndExpiresAtAfter(
            UUID snapshotId, long snapshotVersion, String destinationPostalCode, Instant now);
}
