package br.com.deladopara.orders.application;

import br.com.deladopara.orders.adapter.persistence.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Authorized reads. A guest needs the order's access token, a customer only sees orders of their own account, and
 * an order that is not visible to the caller is reported as not found.
 */
@Service
public class OrderQueryService {

    private final OrderRepository orders;
    private final OrderAccessTokens tokens;

    public OrderQueryService(OrderRepository orders, OrderAccessTokens tokens) {
        this.orders = orders;
        this.tokens = tokens;
    }

    public OrderView forGuest(UUID orderId, String token) {
        if (!tokens.grants(orderId, token)) {
            throw new InvalidOrderTokenException();
        }
        return orders.view(orderId).orElseThrow(OrderNotVisibleException::new);
    }

    public OrderView forAccount(UUID orderId, UUID accountId) {
        return orders.view(orderId)
                .filter(view -> orders.ownedBy(orderId, accountId))
                .orElseThrow(OrderNotVisibleException::new);
    }

    public Page listForAccount(UUID accountId, int page, int size) {
        return page(
                orders.summariesForAccount(accountId, size, offset(page, size)),
                orders.countForAccount(accountId),
                page,
                size);
    }

    public OrderView forAdmin(UUID orderId) {
        return orders.view(orderId).orElseThrow(OrderNotVisibleException::new);
    }

    public Page listForAdmin(int page, int size) {
        return page(orders.summariesForAdmin(size, offset(page, size)), orders.countAll(), page, size);
    }

    private static long offset(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidPageException();
        }
        return (long) page * size;
    }

    private static Page page(List<OrderView.Summary> items, long total, int page, int size) {
        return new Page(items, page, size, total, (int) ((total + size - 1) / size));
    }

    public record Page(List<OrderView.Summary> content, int page, int size, long totalElements, int totalPages) {}

    public static class OrderNotVisibleException extends RuntimeException {}

    public static class InvalidOrderTokenException extends RuntimeException {}

    public static class InvalidPageException extends RuntimeException {}
}
