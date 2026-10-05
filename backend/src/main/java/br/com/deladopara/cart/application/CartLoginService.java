package br.com.deladopara.cart.application;

import br.com.deladopara.cart.adapter.persistence.CartRepository;
import br.com.deladopara.identity.application.CartLoginCoordinator;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartLoginService implements CartLoginCoordinator {
    private static final String PENDING_GUEST_CART_KEY = "cart.pendingGuestSessionKey";

    private final CartRepository carts;

    public CartLoginService(CartRepository carts) {
        this.carts = carts;
    }

    @Transactional
    public boolean prepare(UUID accountId, String previousSessionId, HttpSession authenticatedSession) {
        if (previousSessionId == null || previousSessionId.isBlank()) {
            return false;
        }
        var guestSessionKey = GuestCartService.hashSession(previousSessionId);
        var accountCart = carts.findLockedByAccountId(accountId).orElse(null);
        var guest = carts.findLockedByGuestSessionKey(guestSessionKey).orElse(null);
        if (guest == null) {
            return false;
        }
        if (accountCart != null) {
            authenticatedSession.setAttribute(PENDING_GUEST_CART_KEY, guestSessionKey);
            return true;
        }
        carts.transferGuestCartToAccount(guest.getId(), guestSessionKey, accountId);
        return false;
    }

    public static String pendingGuestKey(HttpSession session) {
        var key = session == null ? null : (String) session.getAttribute(PENDING_GUEST_CART_KEY);
        if (key == null) {
            throw new CartNotFoundException();
        }
        return key;
    }

    public static void clearPending(HttpSession session) {
        if (session != null) {
            session.removeAttribute(PENDING_GUEST_CART_KEY);
        }
    }
}
