package br.com.deladopara.catalog.application;

import br.com.deladopara.catalog.adapter.persistence.ProductImageRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductRepository;
import br.com.deladopara.catalog.adapter.persistence.ProductSkuRepository;
import br.com.deladopara.catalog.adapter.storage.ImageContentProcessor;
import br.com.deladopara.catalog.adapter.storage.LocalImageFileStore;
import br.com.deladopara.catalog.adapter.web.dto.ProductImageResponse;
import br.com.deladopara.catalog.domain.ProductImage;
import br.com.deladopara.catalog.domain.ProductImageDetails;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProductImageService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductImageService.class);

    private final ProductRepository products;
    private final ProductSkuRepository skus;
    private final ProductImageRepository images;
    private final ImageContentProcessor processor;
    private final LocalImageFileStore files;
    private final Clock clock;

    public ProductImageService(
            ProductRepository products,
            ProductSkuRepository skus,
            ProductImageRepository images,
            ImageContentProcessor processor,
            LocalImageFileStore files,
            Clock clock) {
        this.products = products;
        this.skus = skus;
        this.images = images;
        this.processor = processor;
        this.files = files;
        this.clock = clock;
    }

    @Transactional
    public UploadResult upload(
            UUID productId,
            byte[] bytes,
            String altText,
            String source,
            String license,
            String creator,
            String attribution,
            boolean rightsReviewed) {
        var product = products.findById(productId).orElseThrow(ProductService.ProductNotFoundException::new);
        var processed = processor.process(bytes);
        ProductImageDetails details;
        try {
            details = new ProductImageDetails(
                    processed.contentType(),
                    processed.extension(),
                    processed.width(),
                    processed.height(),
                    altText,
                    source,
                    license,
                    creator == null ? "" : creator,
                    attribution == null ? "" : attribution,
                    rightsReviewed);
        } catch (IllegalArgumentException exception) {
            throw new InvalidImageMetadataException(exception.getMessage());
        }

        var previous = images.findByProduct_Id(productId).orElse(null);
        UUID oldKey = previous == null ? null : previous.getStorageKey();
        String oldExtension = previous == null ? null : previous.getExtension();
        var id = previous == null ? UUID.randomUUID() : previous.getId();
        var key = UUID.randomUUID();
        try {
            files.store(key, processed);
        } catch (IOException exception) {
            throw new ImageStorageException();
        }

        try {
            if (previous == null) {
                images.saveAndFlush(new ProductImage(id, product, key, details, clock.instant()));
            } else {
                previous.replace(key, details, clock.instant());
                images.saveAndFlush(previous);
            }
        } catch (RuntimeException exception) {
            deleteQuietly(key, processed.extension());
            throw exception;
        }

        if (previous != null) {
            registerPostCommitDelete(key, processed.extension(), oldKey, oldExtension);
        } else {
            registerRollbackCleanup(key, processed.extension());
        }
        return new UploadResult(
                ProductImageResponse.from(images.findByProduct_Id(productId).orElseThrow()), previous == null);
    }

    @Transactional
    public void remove(UUID productId) {
        var image = images.findByProduct_Id(productId).orElseThrow(ImageNotFoundException::new);
        UUID key = image.getStorageKey();
        String extension = image.getExtension();
        images.delete(image);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key, extension);
            }
        });
    }

    @Transactional(readOnly = true)
    public StoredImage getPublic(UUID storageKey) {
        var image = images.findByStorageKey(storageKey).orElseThrow(ImageNotFoundException::new);
        var product = image.getProduct();
        if (!product.isActive()
                || !product.getProducer().isActive()
                || skus.findAllByProductIdOrderBySkuCode(product.getId()).stream()
                        .noneMatch(sku -> sku.isActive())) {
            throw new ImageNotFoundException();
        }
        try {
            return new StoredImage(
                    MediaType.parseMediaType(image.getContentType()), files.read(storageKey, image.getExtension()));
        } catch (IOException exception) {
            throw new ImageNotFoundException();
        }
    }

    private void registerPostCommitDelete(UUID newKey, String newExtension, UUID oldKey, String oldExtension) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(oldKey, oldExtension);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(newKey, newExtension);
                }
            }
        });
    }

    private void registerRollbackCleanup(UUID key, String extension) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key, extension);
                }
            }
        });
    }

    private void deleteQuietly(UUID key, String extension) {
        try {
            files.delete(key, extension);
        } catch (IOException exception) {
            LOG.warn("Não foi possível remover arquivo de imagem {}", key);
        }
    }

    public record UploadResult(ProductImageResponse image, boolean created) {}

    public record StoredImage(MediaType contentType, byte[] bytes) {
        public StoredImage {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public static class InvalidImageMetadataException extends RuntimeException {
        public InvalidImageMetadataException(String message) {
            super(message);
        }
    }

    public static class ImageNotFoundException extends RuntimeException {}

    public static class ImageStorageException extends RuntimeException {}
}
