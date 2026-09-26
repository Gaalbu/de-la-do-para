package br.com.deladopara.pricing.adapter.web;

import br.com.deladopara.pricing.adapter.web.dto.CouponPageResponse;
import br.com.deladopara.pricing.adapter.web.dto.CouponResponse;
import br.com.deladopara.pricing.adapter.web.dto.CouponUpdateRequest;
import br.com.deladopara.pricing.adapter.web.dto.CouponWriteRequest;
import br.com.deladopara.pricing.application.CouponAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coupons")
public class CouponAdminController {

    private final CouponAdminService coupons;

    public CouponAdminController(CouponAdminService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    public CouponPageResponse list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return coupons.list(page, size);
    }

    @PostMapping
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CouponWriteRequest request) {
        var response = coupons.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/coupons/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public CouponResponse get(@PathVariable UUID id) {
        return coupons.get(id);
    }

    @PatchMapping("/{id}")
    public CouponResponse update(@PathVariable UUID id, @Valid @RequestBody CouponUpdateRequest request) {
        return coupons.update(id, request);
    }
}
