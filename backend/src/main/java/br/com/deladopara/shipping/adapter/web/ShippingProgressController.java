package br.com.deladopara.shipping.adapter.web;

import br.com.deladopara.identity.application.AccountService;
import br.com.deladopara.orders.application.OrderQueryService;
import br.com.deladopara.orders.application.OrderQueryService.InvalidOrderTokenException;
import br.com.deladopara.orders.application.OrderQueryService.OrderNotVisibleException;
import br.com.deladopara.orders.application.OrderView;
import br.com.deladopara.orders.domain.FulfillmentMode;
import br.com.deladopara.shipping.application.ShippingProgressQueryService;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class ShippingProgressController {

    private static final String TOKEN_HEADER = "X-Order-Token";

    private final OrderQueryService orders;
    private final AccountService accounts;
    private final ShippingProgressQueryService progress;

    public ShippingProgressController(
            OrderQueryService orders, AccountService accounts, ShippingProgressQueryService progress) {
        this.orders = orders;
        this.accounts = accounts;
        this.progress = progress;
    }

    @GetMapping("/{id}/shipping-progress")
    public ResponseEntity<ShippingProgressQueryService.DeliveryProgress> get(
            @PathVariable UUID id,
            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
            Authentication authentication) {
        var order = visibleOrder(id, token, authentication);
        if (order.mode() != FulfillmentMode.DELIVERY) {
            throw new OrderNotVisibleException();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(progress.find(id));
    }

    private OrderView visibleOrder(UUID id, String token, Authentication authentication) {
        if (token != null) {
            return orders.forGuest(id, token);
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidOrderTokenException();
        }
        var accountId = accounts.accountIdByEmail(authentication.getName()).orElseThrow(OrderNotVisibleException::new);
        return orders.forAccount(id, accountId);
    }
}
