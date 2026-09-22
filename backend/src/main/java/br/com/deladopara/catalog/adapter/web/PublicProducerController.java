package br.com.deladopara.catalog.adapter.web;

import br.com.deladopara.catalog.adapter.web.dto.PublicProducerPageResponse;
import br.com.deladopara.catalog.application.PublicProducerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/producers")
public class PublicProducerController {

    private final PublicProducerService producers;

    public PublicProducerController(PublicProducerService producers) {
        this.producers = producers;
    }

    @GetMapping("/{slug}")
    public PublicProducerPageResponse get(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return producers.get(slug, page, size);
    }
}
