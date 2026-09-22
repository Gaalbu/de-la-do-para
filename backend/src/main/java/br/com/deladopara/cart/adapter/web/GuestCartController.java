package br.com.deladopara.cart.adapter.web;

import br.com.deladopara.cart.adapter.web.dto.CartItemsRequest;
import br.com.deladopara.cart.adapter.web.dto.CartResponse;
import br.com.deladopara.cart.application.GuestCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class GuestCartController {

    private final GuestCartService carts;

    public GuestCartController(GuestCartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartResponse get(HttpServletRequest request) {
        return carts.get(request.getSession(true).getId());
    }

    @PutMapping("/items")
    public CartResponse replace(@Valid @RequestBody CartItemsRequest body, HttpServletRequest request) {
        return carts.replace(request.getSession(true).getId(), body);
    }

    @DeleteMapping("/items/{skuId}")
    public CartResponse remove(
            @PathVariable UUID skuId, @RequestParam long expectedVersion, HttpServletRequest request) {
        return carts.remove(request.getSession(true).getId(), skuId, expectedVersion);
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear(@RequestParam long expectedVersion, HttpServletRequest request) {
        return ResponseEntity.ok(carts.clear(request.getSession(true).getId(), expectedVersion));
    }
}
