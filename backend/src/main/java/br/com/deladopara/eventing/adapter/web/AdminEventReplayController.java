package br.com.deladopara.eventing.adapter.web;

import br.com.deladopara.eventing.adapter.persistence.EventReplayRepository;
import br.com.deladopara.eventing.adapter.persistence.EventReplayRepository.QuarantinedRecord;
import br.com.deladopara.eventing.application.EventReplayService;
import java.util.List;
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

/** Operator view of quarantined records and audited replay requests (C82). */
@RestController
@RequestMapping("/api/v1/admin/events/quarantine")
public class AdminEventReplayController {

    private static final int LIMIT = 50;

    private final EventReplayRepository records;
    private final EventReplayService replays;

    public AdminEventReplayController(EventReplayRepository records, EventReplayService replays) {
        this.records = records;
        this.replays = replays;
    }

    @GetMapping
    public ResponseEntity<List<QuarantinedRecord>> quarantined() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(records.quarantined(LIMIT));
    }

    @PostMapping("/{topic}/{partition}/{offset}/replays")
    public ResponseEntity<EventReplayService.Requested> replay(
            @PathVariable String topic,
            @PathVariable int partition,
            @PathVariable long offset,
            @RequestBody ReplayRequest request,
            Authentication authentication) {
        var requested = replays.request(topic, partition, offset, authentication.getName(), request.reason());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(requested);
    }

    public record ReplayRequest(String reason) {}
}
