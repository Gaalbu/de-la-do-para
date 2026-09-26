package br.com.deladopara.orders.adapter.web;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.orders.application.OrderQueryService;
import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.application.OrderQueryService.OrderNotVisibleException;
import br.com.deladopara.orders.application.OrderService;
import br.com.deladopara.orders.application.OrderView;
import br.com.deladopara.orders.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    static final String TOKEN_HEADER = "X-Order-Token";

    private final OrderQueryService queries;
    private final AccountService accounts;
    private final OrderService orders;

    public OrderController(OrderQueryService queries, AccountService accounts, OrderService orders) {
        this.queries = queries;
        this.accounts = accounts;
        this.orders = orders;
    }

    /** Guest with the order token, or the owning customer's session; anything else is 401 or 404. */
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderView> get(
            @PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            Authentication auth) {
        if (token != null) {
            return noStore(queries.forGuest(id, token));
        }
        if (auth == null || !auth.isAuthenticated()) {
            throw new InvalidOrderTokenException();
        }
        return noStore(queries.forAccount(id, accountId(auth)));
    }

    @GetMapping("/orders")
    public ResponseEntity<OrderQueryService.Page> list(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return noStore(queries.listForAccount(accountId(auth), page, size));
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<OrderQueryService.Page> adminList(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return noStore(queries.listForAdmin(page, size));
    }

    @GetMapping("/admin/orders/{id}")
    public ResponseEntity<OrderView> adminGet(@PathVariable UUID id) {
        return noStore(queries.forAdmin(id));
    }

    @PostMapping("/admin/orders/{id}/pickup")
    public ResponseEntity<OrderView> pickupAction(@PathVariable UUID id, @Valid @RequestBody PickupActionRequest body) {
        orders.transitionPickup(id, body.action().target(), UUID.randomUUID());
        return noStore(queries.forAdmin(id));
    }

    private UUID accountId(Authentication auth) {
        return accounts.accountIdByEmail(auth.getName()).orElseThrow(OrderNotVisibleException::new);
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }

    public record PickupActionRequest(@NotNull PickupAction action) {}

    public enum PickupAction {
        START_PREPARATION(OrderStatus.PREPARING),
        MARK_READY(OrderStatus.READY_FOR_PICKUP),
        CONFIRM_PICKUP(OrderStatus.PICKED_UP);

        private final OrderStatus target;

        PickupAction(OrderStatus target) {
            this.target = target;
        }

        OrderStatus target() {
            return target;
        }
    }
}
