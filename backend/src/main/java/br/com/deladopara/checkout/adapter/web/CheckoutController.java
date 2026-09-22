package br.com.deladopara.checkout.adapter.web;

import br.com.deladopara.checkout.application.CheckoutSnapshotService;
import br.com.deladopara.checkout.application.PickupOptionsService;
import br.com.deladopara.shipping.application.ShippingQuote;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutSnapshotService snapshots;

    public CheckoutController(CheckoutSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping("/snapshots")
    public SnapshotResponse start(HttpServletRequest request) {
        var snapshot = snapshots.start(request.getSession(true).getId());
        return new SnapshotResponse(snapshot.getId(), snapshot.getCartVersion());
    }

    @GetMapping("/{snapshotId}/delivery-options")
    public DeliveryOptionsResponse deliveryOptions(
            @PathVariable UUID snapshotId,
            @RequestParam long snapshotVersion,
            @RequestParam String postalCode,
            HttpServletRequest request) {
        var options = snapshots.findDeliveryOptions(
                request.getSession(true).getId(), snapshotId, snapshotVersion, postalCode);
        var inputFingerprint = options.stream()
                .map(ShippingQuote::inputFingerprint)
                .findFirst()
                .orElse(null);
        return new DeliveryOptionsResponse(snapshotId, snapshotVersion, inputFingerprint, options);
    }

    @PostMapping("/{snapshotId}/delivery-selection")
    public SelectionResponse selectDeliveryOption(
            @PathVariable UUID snapshotId,
            @RequestParam long snapshotVersion,
            @RequestBody SelectionRequest body,
            HttpServletRequest request) {
        var quote = snapshots.selectDeliveryOption(
                request.getSession(true).getId(), snapshotId, snapshotVersion, body.quoteId(), body.inputFingerprint());
        return new SelectionResponse(quote.id(), quote.snapshotVersion(), quote.inputFingerprint());
    }

    @GetMapping("/{snapshotId}/pickup-options")
    public PickupOptionsService.PickupOptions pickupOptions(
            @PathVariable UUID snapshotId, @RequestParam long snapshotVersion, HttpServletRequest request) {
        return snapshots.findPickupOptions(request.getSession(true).getId(), snapshotId, snapshotVersion);
    }

    @PostMapping("/{snapshotId}/pickup-selection")
    public PickupSelectionResponse selectPickupOption(
            @PathVariable UUID snapshotId,
            @RequestParam long snapshotVersion,
            @RequestBody PickupSelectionRequest body,
            HttpServletRequest request) {
        var option = snapshots.selectPickupOption(
                request.getSession(true).getId(), snapshotId, snapshotVersion, body.pickupOptionId());
        return new PickupSelectionResponse(option.id(), option.point(), option.window(), option.preparationDays());
    }

    public record SnapshotResponse(UUID snapshotId, long snapshotVersion) {}

    public record DeliveryOptionsResponse(
            UUID snapshotId, long snapshotVersion, String inputFingerprint, java.util.List<ShippingQuote> options) {}

    public record SelectionRequest(UUID quoteId, String inputFingerprint) {}

    public record SelectionResponse(UUID quoteId, long snapshotVersion, String inputFingerprint) {}

    public record PickupSelectionRequest(String pickupOptionId) {}

    public record PickupSelectionResponse(String id, String point, String window, int preparationDays) {}

    @ExceptionHandler(java.util.NoSuchElementException.class)
    ResponseEntity<Problem> missing() {
        return problem(HttpStatus.NOT_FOUND, "CHECKOUT_001", "snapshot de checkout não encontrado");
    }

    @ExceptionHandler(CheckoutSnapshotService.SnapshotVersionConflictException.class)
    ResponseEntity<Problem> stale() {
        return problem(HttpStatus.CONFLICT, "CHECKOUT_002", "versão do snapshot está desatualizada");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Problem> invalid(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "CHECKOUT_003", exception.getMessage());
    }

    @ExceptionHandler(br.com.deladopara.shipping.application.DeliveryOptionsService.SelectionConflictException.class)
    ResponseEntity<Problem> selectionConflict() {
        return problem(HttpStatus.CONFLICT, "CHECKOUT_004", "cotação não pertence à seleção atual");
    }

    @ExceptionHandler(br.com.deladopara.shipping.application.DeliveryOptionsService.SelectionExpiredException.class)
    ResponseEntity<Problem> selectionExpired() {
        return problem(HttpStatus.GONE, "CHECKOUT_005", "cotação de entrega expirada");
    }

    @ExceptionHandler(PickupOptionsService.PickupSelectionConflictException.class)
    ResponseEntity<Problem> pickupSelectionConflict() {
        return problem(HttpStatus.CONFLICT, "CHECKOUT_006", "opção de retirada não pertence à seleção atual");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String codigo, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new Problem("Erro", status.value(), detail, codigo, MDC.get("correlationId")));
    }

    record Problem(String title, int status, String detail, String codigo, String correlationId) {}
}
