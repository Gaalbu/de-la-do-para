package br.com.deladopara.catalog.application;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.web.dto.PublicProducerPageResponse;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicProducerService {

    private final ProducerRepository producers;
    private final ProductService products;

    public PublicProducerService(ProducerRepository producers, ProductService products) {
        this.producers = producers;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public PublicProducerPageResponse get(String slug, int page, int size) {
        var producer = producers
                .findBySlug(slug.toLowerCase(Locale.ROOT))
                .filter(found -> found.isActive())
                .orElseThrow(ProducerService.ProducerNotFoundException::new);
        var result = products.findStorefrontProducts(
                new StorefrontQuery(producer.getSlug(), null, null, null, StorefrontQuery.Sort.RELEVANCE, page, size));
        return new PublicProducerPageResponse(
                producer.getSlug(),
                producer.getDisplayName(),
                producer.getOriginLabel(),
                producer.getDescription(),
                result.content(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
