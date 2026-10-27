package br.com.deladopara.checkout.adapter.web;

import br.com.deladopara.checkout.application.CheckoutCancellationService;
import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.orders.application.OrderQueryService;
import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.domain.OrderActor;
import br.com.deladopara.orders.domain.OrderStatus;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cancellation requests (C68). The buyer proves access like on the order page (guest token or owning session); the
 * answer is the order's status after the request, so a repeated request returns the first outcome.
 */
@RestController
@RequestMapping("/api/v1")
public class CancellationController {

    private final CheckoutCancellationService cancellations;
    private final OrderQueryService queries;
    private final AccountService accounts;

    public CancellationController(
            CheckoutCancellationService cancellations, OrderQueryService queries, AccountService accounts) {
        this.cancellations = cancellations;
        this.queries = queries;
        this.accounts = accounts;
    }

    @PostMapping("/orders/{id}/cancellation")
    public ResponseEntity<CancellationResult> cancel(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Order-Token", required = false) String token,
            Authentication authentication) {
        if (token != null) {
            queries.forGuest(id, token);
        } else if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidOrderTokenException();
        } else {
            var accountId =
                    accounts.accountIdByEmail(authentication.getName()).orElseThrow(InvalidOrderTokenException::new);
            queries.forAccount(id, accountId);
        }
        return result(cancellations.cancel(id, OrderActor.CUSTOMER, correlationId()));
    }

    @PostMapping("/admin/orders/{id}/cancellation")
    public ResponseEntity<CancellationResult> adminCancel(@PathVariable UUID id) {
        return result(cancellations.cancel(id, OrderActor.ADMIN, correlationId()));
    }

    private static ResponseEntity<CancellationResult> result(OrderStatus status) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new CancellationResult(status));
    }

    private static UUID correlationId() {
        var value = MDC.get("correlationId");
        return value == null ? UUID.randomUUID() : UUID.fromString(value);
    }

    public record CancellationResult(OrderStatus status) {}
}
