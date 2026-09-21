package br.com.deladopara.catalog.application;

import br.com.deladopara.catalog.adapter.persistence.ProducerRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.adapter.web.dto.ProductPageResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProductResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProductSkuResponse;
import br.com.deladopara.catalog.adapter.web.dto.ProductSkuWriteRequest;
import br.com.deladopara.catalog.adapter.web.dto.ProductUpdateRequest;
import br.com.deladopara.catalog.adapter.web.dto.ProductWriteRequest;
import br.com.deladopara.catalog.adapter.web.dto.PublicProductResponse;
import br.com.deladopara.catalog.domain.Product;
import br.com.deladopara.catalog.domain.ProductSku;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProducerRepository producers;
    private final Clock clock;

    public ProductService(
            ProductRepository products, ProductSkuRepository skus, ProducerRepository producers, Clock clock) {
        this.products = products;
        this.skus = skus;
        this.producers = producers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProductPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new InvalidProductInputException("página deve ser >= 0 e tamanho entre 1 e 50");
        }
        var result = products.findAll(PageRequest.of(page, size, Sort.by("slug").ascending()));
        var pageProducts = result.getContent();
        var productIds = pageProducts.stream().map(Product::getId).toList();
        var loadedSkus = productIds.isEmpty()
                ? List.<ProductSku>of()
                : skus.findAllByProductIdInOrderByProductIdAscSkuCodeAsc(productIds);
        var skusByProduct = loadedSkus.stream()
                .collect(Collectors.groupingBy(sku -> sku.getProduct().getId()));
        var content = pageProducts.stream()
                .map(product -> response(product, skusByProduct.getOrDefault(product.getId(), List.of())))
                .toList();
        return new ProductPageResponse(
                content, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        var product = products.findById(id).orElseThrow(ProductNotFoundException::new);
        return response(product, skus.findAllByProductIdOrderBySkuCode(id));
    }

    @Transactional(readOnly = true)
    public PublicProductResponse getPublic(String slug) {
        var normalizedSlug = slug.toLowerCase(Locale.ROOT);
        var product = products.findBySlug(normalizedSlug).orElseThrow(ProductNotFoundException::new);
        if (!product.isActive() || !product.getProducer().isActive()) {
            throw new ProductNotFoundException();
        }
        var activeSkus = skus.findAllByProductIdOrderBySkuCode(product.getId()).stream()
                .filter(ProductSku::isActive)
                .map(ProductSkuResponse::from)
                .toList();
        if (activeSkus.isEmpty()) {
            throw new ProductNotFoundException();
        }
        return PublicProductResponse.from(product, activeSkus);
    }

    @Transactional
    public ProductResponse create(ProductWriteRequest request) {
        if (request.skus().stream().anyMatch(sku -> sku.id() != null)) {
            throw new InvalidProductInputException("não envie IDs ao criar novas variantes");
        }
        var slug = normalize(request.slug());
        if (products.existsBySlug(slug)) {
            throw new ProductConflictException();
        }
        var producer = producers.findById(request.producerId()).orElseThrow(ProducerNotFoundException::new);
        var now = clock.instant();
        try {
            var product = new Product(
                    UUID.randomUUID(),
                    slug,
                    clean(request.displayName()),
                    clean(request.description()),
                    request.category(),
                    producer,
                    now);
            products.saveAndFlush(product);
            for (var skuRequest : request.skus()) {
                var sku = newSku(product, skuRequest, now);
                skus.saveAndFlush(sku);
            }
            return response(product, skus.findAllByProductIdOrderBySkuCode(product.getId()));
        } catch (IllegalArgumentException exception) {
            throw new InvalidProductInputException(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new ProductConflictException();
            }
            throw exception;
        }
    }

    @Transactional
    public ProductResponse update(UUID id, ProductUpdateRequest request) {
        var product = products.findById(id).orElseThrow(ProductNotFoundException::new);
        var producer = producers.findById(request.producerId()).orElseThrow(ProducerNotFoundException::new);
        var existingSkus = skus.findAllByProductIdOrderBySkuCode(id);
        if (!existingSkus.isEmpty() && product.getCategory() != request.category()) {
            throw new InvalidProductInputException("a categoria não pode mudar após a criação de SKUs");
        }
        var now = clock.instant();
        try {
            product.updateDetails(
                    normalize(request.slug()),
                    clean(request.displayName()),
                    clean(request.description()),
                    request.category(),
                    producer,
                    now);
            if (Boolean.TRUE.equals(request.active())) {
                product.setActive(true, now);
            }
            updateSkus(product, existingSkus, request.skus(), now);
            product.setActive(Boolean.TRUE.equals(request.active()), now);
            products.saveAndFlush(product);
            return response(product, skus.findAllByProductIdOrderBySkuCode(id));
        } catch (IllegalArgumentException exception) {
            throw new InvalidProductInputException(exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                throw new ProductConflictException();
            }
            throw exception;
        }
    }

    private void updateSkus(
            Product product,
            List<ProductSku> existingSkus,
            List<ProductSkuWriteRequest> requestedSkus,
            java.time.Instant now) {
        var existingById = existingSkus.stream().collect(Collectors.toMap(ProductSku::getId, sku -> sku));
        var includedIds = new HashSet<UUID>();
        for (var request : requestedSkus) {
            ProductSku sku;
            if (request.id() == null) {
                sku = newSku(product, request, now);
            } else {
                if (!includedIds.add(request.id())) {
                    throw new InvalidProductInputException("uma variante foi enviada mais de uma vez");
                }
                sku = existingById.get(request.id());
                if (sku == null) {
                    throw new InvalidProductInputException("a variante não pertence a este produto");
                }
                if (Boolean.TRUE.equals(request.active())
                        && (!product.isActive() || !product.getProducer().isActive())) {
                    throw new InvalidProductInputException("só é possível ativar SKU de produto e produtor ativos");
                }
                sku.updatePackaging(
                        product,
                        request.skuCode(),
                        request.salesUnit(),
                        request.netContentGrams(),
                        request.minimumShelfLifeDays(),
                        request.fragile(),
                        request.lengthMm(),
                        request.widthMm(),
                        request.heightMm(),
                        request.grossWeightGrams(),
                        now);
            }
            sku.setActive(Boolean.TRUE.equals(request.active()), now);
            skus.saveAndFlush(sku);
        }
        for (var sku : existingSkus) {
            if (!includedIds.contains(sku.getId())) {
                sku.setActive(false, now);
            }
        }
    }

    private ProductSku newSku(Product product, ProductSkuWriteRequest request, java.time.Instant now) {
        if (Boolean.TRUE.equals(request.active())
                && (!product.isActive() || !product.getProducer().isActive())) {
            throw new InvalidProductInputException("só é possível criar SKU ativo para produto e produtor ativos");
        }
        var sku = new ProductSku(
                request.id() == null ? UUID.randomUUID() : request.id(),
                product,
                request.skuCode(),
                request.salesUnit(),
                request.netContentGrams(),
                request.minimumShelfLifeDays(),
                request.fragile(),
                request.lengthMm(),
                request.widthMm(),
                request.heightMm(),
                request.grossWeightGrams(),
                now);
        sku.setActive(Boolean.TRUE.equals(request.active()), now);
        return sku;
    }

    private static ProductResponse response(Product product, List<ProductSku> productSkus) {
        return ProductResponse.from(
                product, productSkus.stream().map(ProductSkuResponse::from).toList());
    }

    private static String normalize(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
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

    public static class ProductNotFoundException extends RuntimeException {}

    public static class ProducerNotFoundException extends RuntimeException {}

    public static class ProductConflictException extends RuntimeException {}

    public static class InvalidProductInputException extends RuntimeException {
        public InvalidProductInputException(String message) {
            super(message);
        }
    }
}
