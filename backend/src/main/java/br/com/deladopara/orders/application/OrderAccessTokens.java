package br.com.deladopara.orders.application;

import br.com.deladopara.orders.adapter.persistence.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guest proof of access to one order: random token shown once, stored only as a SHA-256 hash. */
@Service
public class OrderAccessTokens {

    private static final int TOKEN_BYTES = 32;

    private final OrderRepository orders;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderAccessTokens(OrderRepository orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    /** Returns the raw token; it cannot be recovered later. */
    @Transactional
    public String issue(UUID orderId) {
        var bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        orders.insertAccessToken(hash(token), orderId, clock.instant());
        return token;
    }

    public boolean grants(UUID orderId, String token) {
        return token != null && !token.isBlank() && orders.accessTokenGrants(hash(token), orderId);
    }

    static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
