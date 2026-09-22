package br.com.deladopara.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false, unique = true)
    private Product product;

    @Column(name = "storage_key", nullable = false, unique = true)
    private UUID storageKey;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "extension", nullable = false, length = 8)
    private String extension;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Column(name = "alt_text", nullable = false, length = 250)
    private String altText;

    @Column(name = "source", nullable = false, length = 1500)
    private String source;

    @Column(name = "license", nullable = false, length = 200)
    private String license;

    @Column(name = "creator", nullable = false, length = 200)
    private String creator;

    @Column(name = "attribution", nullable = false, length = 500)
    private String attribution;

    @Column(name = "rights_reviewed", nullable = false)
    private boolean rightsReviewed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductImage() {}

    public ProductImage(UUID id, Product product, UUID storageKey, ProductImageDetails details, Instant now) {
        if (id == null || product == null || storageKey == null || now == null) {
            throw new IllegalArgumentException("Image identity, product, storage key, and time are required");
        }
        this.id = id;
        this.product = product;
        this.createdAt = now;
        apply(storageKey, details, now);
    }

    public void replace(UUID newStorageKey, ProductImageDetails details, Instant now) {
        if (newStorageKey == null || now == null) {
            throw new IllegalArgumentException("Image storage key and update time are required");
        }
        apply(newStorageKey, details, now);
    }

    private void apply(UUID key, ProductImageDetails details, Instant now) {
        if (details == null) {
            throw new IllegalArgumentException("Image details are required");
        }
        storageKey = key;
        contentType = details.contentType();
        extension = details.extension();
        width = details.width();
        height = details.height();
        altText = details.altText();
        source = details.source();
        license = details.license();
        creator = details.creator();
        attribution = details.attribution();
        rightsReviewed = details.rightsReviewed();
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return product.getId();
    }

    public Product getProduct() {
        return product;
    }

    public UUID getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getAltText() {
        return altText;
    }

    public String getSource() {
        return source;
    }

    public String getLicense() {
        return license;
    }

    public String getCreator() {
        return creator;
    }

    public String getAttribution() {
        return attribution;
    }

    public boolean isRightsReviewed() {
        return rightsReviewed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
