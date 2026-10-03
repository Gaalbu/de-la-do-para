package br.com.deladopara.shipping.adapter.web;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.orders.application.OrderQueryService;
import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.shipping.application.PickupService;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PickupController {

    private final PickupService pickups;
    private final OrderQueryService queries;
    private final AccountService accounts;

    public PickupController(PickupService pickups, OrderQueryService queries, AccountService accounts) {
        this.pickups = pickups;
        this.queries = queries;
        this.accounts = accounts;
    }

    @GetMapping("/orders/{id}/pickup")
    public ResponseEntity<PickupService.PickupInfo> pickup(
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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(pickups.pickupInfo(id));
    }

    @PostMapping("/admin/orders/{id}/pickup/prepare")
    public ResponseEntity<Void> startPreparation(@PathVariable UUID id) {
        pickups.startPreparation(id, correlationId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/orders/{id}/pickup/ready")
    public ResponseEntity<Void> markReady(@PathVariable UUID id) {
        pickups.markReady(id, correlationId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/orders/{id}/pickup/confirm")
    public ResponseEntity<Void> confirm(@PathVariable UUID id, @RequestBody ConfirmRequest request) {
        pickups.confirm(id, request.code(), correlationId());
        return ResponseEntity.noContent().build();
    }

    public record ConfirmRequest(String code) {}

    private static UUID correlationId() {
        var value = MDC.get("correlationId");
        return value == null ? UUID.randomUUID() : UUID.fromString(value);
    }
}
