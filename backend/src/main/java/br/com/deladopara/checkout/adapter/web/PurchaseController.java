package br.com.deladopara.checkout.adapter.web;

import br.com.deladopara.checkout.application.CheckoutIdempotency.IdempotencyKeyReusedException;
import br.com.deladopara.checkout.application.CheckoutIdempotency.InvalidIdempotencyKeyException;
import br.com.deladopara.checkout.application.CheckoutIdempotency.PurchaseInProgressException;
import br.com.deladopara.checkout.application.CheckoutSnapshotService.SnapshotVersionConflictException;
import br.com.deladopara.checkout.application.PickupOptionsService.PickupSelectionConflictException;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.AcceptedPurchase;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.Address;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.CouponRejectedException;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.InvalidContactEmailException;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.PurchaseCommand;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.SummaryChangedException;
import br.com.deladopara.checkout.application.PurchaseAcceptanceService.ZeroTotalException;
import br.com.deladopara.checkout.application.PurchaseSummaryService;
import br.com.deladopara.checkout.application.PurchaseSummaryService.CouponUnavailableException;
import br.com.deladopara.checkout.application.PurchaseSummaryService.InvalidSelectionException;
import br.com.deladopara.checkout.application.PurchaseSummaryService.ItemUnavailableException;
import br.com.deladopara.checkout.application.PurchaseSummaryService.Mode;
import br.com.deladopara.checkout.application.PurchaseSummaryService.PurchaseSummary;
import br.com.deladopara.checkout.application.PurchaseSummaryService.Selection;
import br.com.deladopara.checkout.application.PurchaseSummaryService.SnapshotNotFoundException;
import br.com.deladopara.inventory.application.StockReservationService.InsufficientStockException;
import br.com.deladopara.shipping.application.DeliveryOptionsService.SelectionConflictException;
import br.com.deladopara.shipping.application.DeliveryOptionsService.SelectionExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/v1/checkout/{snapshotId}")
public class PurchaseController {

    private final PurchaseSummaryService summaries;
    private final PurchaseAcceptanceService purchases;

    public PurchaseController(PurchaseSummaryService summaries, PurchaseAcceptanceService purchases) {
        this.summaries = summaries;
        this.purchases = purchases;
    }

    @GetMapping("/summary")
    public PurchaseSummary summary(
            @PathVariable UUID snapshotId,
            @RequestParam long snapshotVersion,
            @RequestParam Mode mode,
            @RequestParam(required = false) UUID quoteId,
            @RequestParam(required = false) String inputFingerprint,
            @RequestParam(required = false) String pickupOptionId,
            @RequestParam(required = false) String couponCode,
            HttpServletRequest request) {
        return summaries.summarize(
                request.getSession(true).getId(),
                snapshotId,
                snapshotVersion,
                new Selection(mode, quoteId, inputFingerprint, pickupOptionId, couponCode));
    }

    @PostMapping("/purchase")
    public ResponseEntity<AcceptedPurchase> purchase(
            @PathVariable UUID snapshotId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PurchaseRequest body,
            Authentication auth,
            HttpServletRequest request) {
        var accountEmail = auth == null || auth instanceof AnonymousAuthenticationToken ? null : auth.getName();
        var accepted = purchases.accept(new PurchaseCommand(
                request.getSession(true).getId(),
                accountEmail,
                idempotencyKey,
                snapshotId,
                body.snapshotVersion(),
                new Selection(
                        body.mode(), body.quoteId(), body.inputFingerprint(), body.pickupOptionId(), body.couponCode()),
                body.email(),
                body.address() == null ? null : body.address().toAddress(),
                body.summaryVersion()));
        var response = accepted.replayed()
                ? ResponseEntity.ok()
                : ResponseEntity.created(URI.create("/api/v1/orders/" + accepted.orderId()));
        return response.cacheControl(CacheControl.noStore().cachePrivate()).body(accepted);
    }

    public record PurchaseRequest(
            long snapshotVersion,
            @NotNull Mode mode,
            UUID quoteId,
            String inputFingerprint,
            String pickupOptionId,
            @Size(max = 64) String couponCode,
            @Size(max = 254) String email,
            @Valid AddressRequest address,
            @NotBlank String summaryVersion) {}

    public record AddressRequest(
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Size(max = 160) String street,
            @NotBlank @Size(max = 20) String number,
            @Size(max = 120) String complement,
            @NotBlank @Size(max = 120) String district,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(min = 2, max = 2) String state) {

        Address toAddress() {
            return new Address(recipientName, street, number, complement, district, city, state);
        }
    }

    @ExceptionHandler({SnapshotNotFoundException.class})
    ResponseEntity<Problem> missing() {
        return problem(HttpStatus.NOT_FOUND, "CHECKOUT_001", "snapshot de checkout não encontrado");
    }

    @ExceptionHandler(SnapshotVersionConflictException.class)
    ResponseEntity<Problem> stale() {
        return problem(HttpStatus.CONFLICT, "CHECKOUT_002", "versão do snapshot está desatualizada");
    }

    @ExceptionHandler({
        InvalidSelectionException.class,
        InvalidContactEmailException.class,
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<Problem> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "CHECKOUT_003", "dados de compra inválidos");
    }

    @ExceptionHandler({SelectionConflictException.class, PickupSelectionConflictException.class})
    ResponseEntity<Problem> selectionConflict() {
        return problem(
                HttpStatus.CONFLICT, "CHECKOUT_004", "opção de entrega ou retirada não pertence à seleção atual");
    }

    @ExceptionHandler(SelectionExpiredException.class)
    ResponseEntity<Problem> selectionExpired() {
        return problem(HttpStatus.GONE, "CHECKOUT_005", "cotação de entrega expirada");
    }

    @ExceptionHandler(PurchaseInProgressException.class)
    ResponseEntity<Problem> inProgress() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "1")
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(body(HttpStatus.CONFLICT, "CHECKOUT_010", "compra com esta chave ainda em processamento"));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<Problem> keyReused() {
        return problem(
                HttpStatus.UNPROCESSABLE_CONTENT, "CHECKOUT_011", "chave de idempotência usada com outra compra");
    }

    @ExceptionHandler(SummaryChangedException.class)
    ResponseEntity<SummaryChanged> summaryChanged(SummaryChangedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(new SummaryChanged(
                        "Erro",
                        409,
                        "o resumo da compra mudou; confirme os novos valores",
                        "CHECKOUT_012",
                        MDC.get("correlationId"),
                        exception.summary()));
    }

    @ExceptionHandler({InsufficientStockException.class, ItemUnavailableException.class})
    ResponseEntity<Problem> outOfStock() {
        return problem(HttpStatus.CONFLICT, "CHECKOUT_013", "algum item não está mais disponível na quantidade pedida");
    }

    @ExceptionHandler({CouponRejectedException.class, CouponUnavailableException.class})
    ResponseEntity<Problem> couponRejected(RuntimeException exception) {
        var reason = exception.getMessage() == null ? "UNAVAILABLE" : exception.getMessage();
        return problem(HttpStatus.CONFLICT, "CHECKOUT_014", "cupom indisponível: " + reason);
    }

    @ExceptionHandler({InvalidIdempotencyKeyException.class, MissingRequestHeaderException.class})
    ResponseEntity<Problem> badKey() {
        return problem(HttpStatus.BAD_REQUEST, "CHECKOUT_015", "Idempotency-Key ausente ou inválida");
    }

    @ExceptionHandler(ZeroTotalException.class)
    ResponseEntity<Problem> zeroTotal() {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "CHECKOUT_016", "compra com total zero não é suportada");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String codigo, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("application/problem+json"))
                .body(body(status, codigo, detail));
    }

    private static Problem body(HttpStatus status, String codigo, String detail) {
        return new Problem("Erro", status.value(), detail, codigo, MDC.get("correlationId"));
    }

    record Problem(String title, int status, String detail, String codigo, String correlationId) {}

    record SummaryChanged(
            String title, int status, String detail, String codigo, String correlationId, PurchaseSummary summary) {}
}
