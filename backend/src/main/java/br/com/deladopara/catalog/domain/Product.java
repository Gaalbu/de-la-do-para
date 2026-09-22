package br.com.deladopara.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producer_id", nullable = false)
    private Producer producer;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(
            UUID id,
            String slug,
            String displayName,
            String description,
            Category category,
            Producer producer,
            Instant now) {
        if (id == null || now == null) {
            throw new IllegalArgumentException("Product identity and creation time are required");
        }
        this.id = id;
        this.createdAt = now;
        this.active = true;
        updateDetails(slug, displayName, description, category, producer, now);
    }

    public void updateDetails(
            String slug, String displayName, String description, Category category, Producer producer, Instant now) {
        requireText(slug, "slug", 80);
        var normalizedSlug = slug.toLowerCase(Locale.ROOT);
        if (normalizedSlug.length() > 80 || !normalizedSlug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Product slug has an invalid format");
        }
        requireText(displayName, "display name", 160);
        requireText(description, "description", 2000);
        if (category == null || producer == null || now == null) {
            throw new IllegalArgumentException("Product category, producer, and update time are required");
        }
        boolean producerChanged =
                this.producer == null || !this.producer.getId().equals(producer.getId());
        if (producerChanged && !producer.isActive()) {
            throw new IllegalArgumentException("New or updated products require an active producer");
        }
        this.slug = normalizedSlug;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.producer = producer;
        this.updatedAt = now;
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException("Product " + field + " is required");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Producer getProducer() {
        return producer;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setActive(boolean active, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Update time is required");
        }
        this.active = active;
        this.updatedAt = now;
    }

    public enum Category {
        FOOD,
        CRAFT
    }
}
