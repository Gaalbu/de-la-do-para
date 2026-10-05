package br.com.deladopara.shipping.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** One durable external write for a stable shipping unit derived from the accepted package snapshot. */
public record ShippingLabelOperation(
        UUID id,
        UUID orderId,
        UUID shipmentUnitId,
        List<Integer> packageSequences,
        Step step,
        State state,
        UUID correlationId,
        String providerShipmentId,
        String failureCode,
        Instant createdAt,
        Instant updatedAt,
        Instant requestedAt,
        Instant resolvedAt) {

    public ShippingLabelOperation {
        if (id == null || orderId == null || shipmentUnitId == null || correlationId == null) {
            throw new IllegalArgumentException("shipping operation identity is required");
        }
        if (packageSequences == null
                || packageSequences.isEmpty()
                || packageSequences.stream().anyMatch(sequence -> sequence == null || sequence < 1)
                || new HashSet<>(packageSequences).size() != packageSequences.size()) {
            throw new IllegalArgumentException("shipping operation package sequences are invalid");
        }
        packageSequences = List.copyOf(packageSequences);
        if (step == null || state == null || createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("shipping operation state is invalid");
        }
        if (state == State.READY && (requestedAt != null || resolvedAt != null || failureCode != null)) {
            throw new IllegalArgumentException("ready shipping operation cannot have a request result");
        }
        if (state != State.READY && requestedAt == null) {
            throw new IllegalArgumentException("requested shipping operation needs a request time");
        }
        if ((state == State.SUCCEEDED || state == State.FAILED || state == State.UNKNOWN) != (resolvedAt != null)) {
            throw new IllegalArgumentException("resolved shipping operation needs a resolution time");
        }
        if (state == State.FAILED && !validFailureCode(failureCode)) {
            throw new IllegalArgumentException("shipping operation failure code is invalid");
        }
        if (state != State.FAILED && failureCode != null) {
            throw new IllegalArgumentException("non-failed shipping operation cannot have a failure code");
        }
        if (providerShipmentId != null && providerShipmentId.isBlank()) {
            throw new IllegalArgumentException("provider shipment id cannot be blank");
        }
        if (providerShipmentId != null && providerShipmentId.length() > 160) {
            throw new IllegalArgumentException("provider shipment id is invalid");
        }
        if (step != Step.ADD_TO_CART && providerShipmentId == null) {
            throw new IllegalArgumentException("follow-up shipping operation needs a provider shipment id");
        }
        if (requestedAt != null && requestedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("shipping operation request time is invalid");
        }
        if (resolvedAt != null && (resolvedAt.isBefore(requestedAt) || resolvedAt.isAfter(updatedAt))) {
            throw new IllegalArgumentException("shipping operation resolution time is invalid");
        }
    }

    public static ShippingLabelOperation ready(
            UUID id,
            UUID orderId,
            UUID shipmentUnitId,
            List<Integer> packageSequences,
            Step step,
            UUID correlationId,
            Instant now) {
        return ready(id, orderId, shipmentUnitId, packageSequences, step, correlationId, null, now);
    }

    public static ShippingLabelOperation ready(
            UUID id,
            UUID orderId,
            UUID shipmentUnitId,
            List<Integer> packageSequences,
            Step step,
            UUID correlationId,
            String providerShipmentId,
            Instant now) {
        return new ShippingLabelOperation(
                id,
                orderId,
                shipmentUnitId,
                packageSequences,
                step,
                State.READY,
                correlationId,
                providerShipmentId,
                null,
                now,
                now,
                null,
                null);
    }

    public ShippingLabelOperation request(Instant now) {
        requireState(State.READY);
        requireLater(now);
        return new ShippingLabelOperation(
                id,
                orderId,
                shipmentUnitId,
                packageSequences,
                step,
                State.REQUESTED,
                correlationId,
                providerShipmentId,
                null,
                createdAt,
                now,
                now,
                null);
    }

    public ShippingLabelOperation succeed(String resultProviderShipmentId, Instant now) {
        requireState(State.REQUESTED);
        requireLater(now);
        var resolvedProviderId = mergeProviderId(resultProviderShipmentId);
        return resolved(State.SUCCEEDED, resolvedProviderId, null, now);
    }

    public ShippingLabelOperation fail(String deterministicFailureCode, Instant now) {
        requireState(State.REQUESTED);
        requireLater(now);
        if (!validFailureCode(deterministicFailureCode)) {
            throw new IllegalArgumentException("shipping operation failure code is invalid");
        }
        return resolved(State.FAILED, providerShipmentId, deterministicFailureCode, now);
    }

    public ShippingLabelOperation markUnknown(Instant now) {
        requireState(State.REQUESTED);
        requireLater(now);
        return resolved(State.UNKNOWN, providerShipmentId, null, now);
    }

    /** Called only after a separately authorized lookup establishes that the external write succeeded. */
    public ShippingLabelOperation reconcileSucceeded(String confirmedProviderShipmentId, Instant now) {
        requireState(State.UNKNOWN);
        requireLater(now);
        return resolved(State.SUCCEEDED, mergeProviderId(confirmedProviderShipmentId), null, now);
    }

    /** Called only after a separately authorized lookup establishes that the external write did not occur. */
    public ShippingLabelOperation reconcileNotApplied(String sanitizedFailureCode, Instant now) {
        requireState(State.UNKNOWN);
        requireLater(now);
        if (!validFailureCode(sanitizedFailureCode)) {
            throw new IllegalArgumentException("shipping operation failure code is invalid");
        }
        return resolved(State.FAILED, providerShipmentId, sanitizedFailureCode, now);
    }

    private ShippingLabelOperation resolved(State next, String providerId, String errorCode, Instant now) {
        return new ShippingLabelOperation(
                id,
                orderId,
                shipmentUnitId,
                packageSequences,
                step,
                next,
                correlationId,
                providerId,
                errorCode,
                createdAt,
                now,
                requestedAt,
                now);
    }

    private String mergeProviderId(String resultProviderShipmentId) {
        if (resultProviderShipmentId == null || resultProviderShipmentId.isBlank()) {
            if (providerShipmentId == null) {
                throw new IllegalArgumentException("provider shipment id is required after this operation");
            }
            return providerShipmentId;
        }
        if (providerShipmentId != null && !providerShipmentId.equals(resultProviderShipmentId)) {
            throw new IllegalArgumentException("provider shipment id cannot change between steps");
        }
        return resultProviderShipmentId;
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("shipping operation state does not allow this transition");
        }
    }

    private void requireLater(Instant now) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("shipping operation timestamp is invalid");
        }
    }

    private static boolean validFailureCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,79}");
    }

    public enum Step {
        ADD_TO_CART,
        PURCHASE,
        GENERATE
    }

    public enum State {
        READY,
        REQUESTED,
        SUCCEEDED,
        FAILED,
        UNKNOWN
    }
}
