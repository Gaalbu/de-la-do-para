package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.ProducerPageResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProducerResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProducerUpdateRequest;
import br.com.deladopara.catalog.adapter.web.dto.ProducerWriteRequest;
import br.com.deladopara.catalog.application.ProducerService;
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
@RequestMapping("/api/v1/admin/producers")
public class ProducerAdminController {

    private final ProducerService producers;

    public ProducerAdminController(ProducerService producers) {
        this.producers = producers;
    }

    @GetMapping
    public ProducerPageResponse list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return producers.list(page, size);
    }

    @PostMapping
    public ResponseEntity<ProducerResponse> create(@Valid @RequestBody ProducerWriteRequest request) {
        var response = producers.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/producers/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ProducerResponse get(@PathVariable UUID id) {
        return producers.get(id);
    }

    @PatchMapping("/{id}")
    public ProducerResponse update(@PathVariable UUID id, @Valid @RequestBody ProducerUpdateRequest request) {
        return producers.update(id, request);
    }
}
