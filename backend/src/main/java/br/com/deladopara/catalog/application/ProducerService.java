package br.com.deladopara.catalog.application;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.web.dto.ProducerPageResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProducerResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProducerUpdateRequest;
import br.com.deladopara.catalog.adapter.web.dto.ProducerWriteRequest;
import br.com.deladopara.catalog.domain.Producer;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProducerService {

    private final ProducerRepository producers;
    private final Clock clock;

    public ProducerService(ProducerRepository producers, Clock clock) {
        this.producers = producers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProducerPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidProducerInputException("CATALOG_001", "página deve ser >= 0 e tamanho entre 1 e 50");
        }
        var result =
                producers.findAll(PageRequest.of(page, size, Sort.by("slug").ascending()));
        return new ProducerPageResponse(
                result.getContent().stream().map(ProducerResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProducerResponse get(UUID id) {
        return producers.findById(id).map(ProducerResponse::from).orElseThrow(ProducerNotFoundException::new);
    }

    @Transactional
    public ProducerResponse create(ProducerWriteRequest request) {
        var slug = normalize(request.slug());
        if (producers.existsBySlug(slug)) {
            throw new ProducerSlugConflictException();
        }
        var now = clock.instant();
        var producer = new Producer(
                UUID.randomUUID(),
                slug,
                clean(request.displayName()),
                clean(request.originLabel()),
                clean(request.description()),
                now);
        return save(producer);
    }

    @Transactional
    public ProducerResponse update(UUID id, ProducerUpdateRequest request) {
        var producer = producers.findById(id).orElseThrow(ProducerNotFoundException::new);
        var slug = normalize(request.slug());
        if (!slug.equals(producer.getSlug()) && producers.existsBySlug(slug)) {
            throw new ProducerSlugConflictException();
        }
        var now = clock.instant();
        producer.updateDetails(
                slug, clean(request.displayName()), clean(request.originLabel()), clean(request.description()), now);
        producer.setActive(Boolean.TRUE.equals(request.active()), now);
        return save(producer);
    }

    private ProducerResponse save(Producer producer) {
        try {
            return ProducerResponse.from(producers.saveAndFlush(producer));
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new ProducerSlugConflictException();
            }
            throw exception;
        }
    }

    private static String normalize(String slug) {
        return slug.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && "23505".equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    public static class ProducerNotFoundException extends RuntimeException {}

    public static class ProducerSlugConflictException extends RuntimeException {}

    public static class InvalidProducerInputException extends RuntimeException {
        private final String code;

        public InvalidProducerInputException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
