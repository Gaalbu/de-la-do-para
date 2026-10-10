package br.com.deladopara.shipping.adapter.persistence;

import br.com.deladopara.shipping.domain.ShippingLabelOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShippingLabelOperationRepository extends JpaRepository<ShippingLabelOperationEntity, UUID> {

    Optional<ShippingLabelOperationEntity> findByOrderIdAndShipmentUnitIdAndStep(
            UUID orderId, UUID shipmentUnitId, ShippingLabelOperation.Step step);
}
