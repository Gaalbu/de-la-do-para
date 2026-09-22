package br.com.deladopara.checkout.adapter.web;

import br.com.deladopara.checkout.application.CheckoutSnapshotService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {

    private final CheckoutSnapshotService snapshots;

    public CheckoutController(CheckoutSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping("/snapshots")
    public SnapshotResponse start(HttpServletRequest request) {
        var snapshot = snapshots.start(request.getSession(true).getId());
        return new SnapshotResponse(snapshot.getId(), snapshot.getCartVersion());
    }

    public record SnapshotResponse(UUID snapshotId, long snapshotVersion) {}
}
