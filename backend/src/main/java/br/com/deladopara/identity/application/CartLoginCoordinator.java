package br.com.deladopara.identity.application;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

/** Optional login hook implemented by the cart module without coupling identity to cart internals. */
public interface CartLoginCoordinator {
    boolean prepare(UUID accountId, String previousSessionId, HttpSession authenticatedSession);
}
