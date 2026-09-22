package br.com.deladopara.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "producers")
public class Producer {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "origin_label", nullable = false, length = 300)
    private String originLabel;

    @Column(name = "description", nullable = false, length = 2000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Producer() {}

    public Producer(UUID id, String slug, String displayName, String originLabel, String description, Instant now) {
        this.id = id;
        this.createdAt = now;
        this.active = true;
        updateDetails(slug, displayName, originLabel, description, now);
    }

    public void updateDetails(String slug, String displayName, String originLabel, String description, Instant now) {
        this.slug = slug.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.originLabel = originLabel;
        this.description = description;
        this.updatedAt = now;
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

    public String getOriginLabel() {
        return originLabel;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }
}
