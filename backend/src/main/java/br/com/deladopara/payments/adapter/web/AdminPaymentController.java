package br.com.deladopara.payments.adapter.web;

import br.com.deladopara.payments.adapter.persistence.PaymentRepository;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository.AttentionIntent;
import br.com.deladopara.payments.adapter.persistence.PaymentRepository.OperationTrail;
import br.com.deladopara.payments.application.AdminPaymentLookups;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator view of uncertain payments (C82a): what still needs recovery, with every provider operation, and a way to
 * queue one more lookup. Nothing here calls the provider or creates a charge or refund.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {

    private static final int LIMIT = 50;

    private final PaymentRepository payments;
    private final AdminPaymentLookups lookups;

    public AdminPaymentController(PaymentRepository payments, AdminPaymentLookups lookups) {
        this.payments = payments;
        this.lookups = lookups;
    }

    @GetMapping("/attention")
    public ResponseEntity<List<AttentionView>> attention() {
        var body = payments.needingAttention(LIMIT).stream()
                .map(intent -> new AttentionView(intent, payments.operationTrail(intent.id())))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(body);
    }

    @PostMapping("/{intentId}/lookups")
    public ResponseEntity<AdminPaymentLookups.Requested> requestLookup(
            @PathVariable UUID intentId, @RequestBody LookupRequest request, Authentication authentication) {
        var requested = lookups.request(intentId, authentication.getName(), request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(requested);
    }

    public record LookupRequest(String reason) {}

    public record AttentionView(AttentionIntent intent, List<OperationTrail> operations) {}
}
