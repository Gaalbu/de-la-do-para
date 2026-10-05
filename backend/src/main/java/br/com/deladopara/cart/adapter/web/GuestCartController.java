package br.com.deladopara.cart.adapter.web;

import br.com.deladopara.cart.adapter.web.dto.CartItemsRequest;
import br.com.deladopara.cart.adapter.web.dto.CartMergeRequest;
import br.com.deladopara.cart.adapter.web.dto.CartMergeView;
import br.com.deladopara.cart.adapter.web.dto.CartResponse;
import br.com.deladopara.cart.application.CartLoginService;
import br.com.deladopara.cart.application.CartMergeService;
import br.com.deladopara.cart.application.GuestCartService;
import br.com.deladopara.identity.adapter.persistence.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class GuestCartController {

    private final GuestCartService carts;
    private final CartMergeService merges;
    private final AccountRepository accounts;

    public GuestCartController(GuestCartService carts, CartMergeService merges, AccountRepository accounts) {
        this.carts = carts;
        this.merges = merges;
        this.accounts = accounts;
    }

    @GetMapping
    public CartResponse get(HttpServletRequest request, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return carts.getAccount(authentication.getName());
        }
        return carts.get(request.getSession(true).getId());
    }

    @PutMapping("/items")
    public CartResponse replace(
            @Valid @RequestBody CartItemsRequest body, HttpServletRequest request, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return carts.replaceAccount(authentication.getName(), body);
        }
        return carts.replace(request.getSession(true).getId(), body);
    }

    @DeleteMapping("/items/{skuId}")
    public CartResponse remove(
            @PathVariable UUID skuId,
            @RequestParam long expectedVersion,
            HttpServletRequest request,
            Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return carts.removeAccount(authentication.getName(), skuId, expectedVersion);
        }
        return carts.remove(request.getSession(true).getId(), skuId, expectedVersion);
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear(
            @RequestParam long expectedVersion, HttpServletRequest request, Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return ResponseEntity.ok(carts.clearAccount(authentication.getName(), expectedVersion));
        }
        return ResponseEntity.ok(carts.clear(request.getSession(true).getId(), expectedVersion));
    }

    @GetMapping("/merge")
    public CartMergeView getMerge(Authentication authentication, HttpServletRequest request) {
        var accountId = accountId(authentication);
        var guestKey = pendingGuestKey(request);
        return merges.get(accountId, guestKey);
    }

    @PostMapping("/merge")
    public CartResponse resolveMerge(
            @Valid @RequestBody CartMergeRequest body, Authentication authentication, HttpServletRequest request) {
        var session = request.getSession(false);
        var key = pendingGuestKey(request);
        var result = merges.resolve(accountId(authentication), key, body);
        CartLoginService.clearPending(session);
        return result;
    }

    private String pendingGuestKey(HttpServletRequest request) {
        return CartLoginService.pendingGuestKey(request.getSession(false));
    }

    private UUID accountId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Não autenticado");
        }
        return accounts.findByEmailIgnoreCase(authentication.getName())
                .map(account -> account.getId())
                .orElseThrow(() ->
                        new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                                "Não autenticado"));
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
