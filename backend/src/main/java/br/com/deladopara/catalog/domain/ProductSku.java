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
@Table(name = "product_skus")
public class ProductSku {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_category", nullable = false, updatable = false, length = 16)
    private Product.Category productCategory;

    @Column(name = "sku_code", nullable = false, length = 80)
    private String skuCode;

    @Column(name = "sales_unit", nullable = false, length = 40)
    private String salesUnit;

    @Column(name = "net_content_grams")
    private Integer netContentGrams;

    @Column(name = "minimum_shelf_life_days")
    private Integer minimumShelfLifeDays;

    @Column(name = "fragile", nullable = false)
    private boolean fragile;

    @Column(name = "length_mm", nullable = false)
    private int lengthMm;

    @Column(name = "width_mm", nullable = false)
    private int widthMm;

    @Column(name = "height_mm", nullable = false)
    private int heightMm;

    @Column(name = "gross_weight_grams", nullable = false)
    private int grossWeightGrams;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductSku() {}

    public ProductSku(
            UUID id,
            Product product,
            String skuCode,
            String salesUnit,
            Integer netContentGrams,
            Integer minimumShelfLifeDays,
            boolean fragile,
            int lengthMm,
            int widthMm,
            int heightMm,
            int grossWeightGrams,
            Instant now) {
        if (id == null || now == null) {
            throw new IllegalArgumentException("SKU identity and creation time are required");
        }
        this.id = id;
        this.createdAt = now;
        this.active = true;
        this.productCategory = product == null ? null : product.getCategory();
        updatePackaging(
                product,
                skuCode,
                salesUnit,
                netContentGrams,
                minimumShelfLifeDays,
                fragile,
                lengthMm,
                widthMm,
                heightMm,
                grossWeightGrams,
                now);
    }

    public void updatePackaging(
            Product product,
            String skuCode,
            String salesUnit,
            Integer netContentGrams,
            Integer minimumShelfLifeDays,
            boolean fragile,
            int lengthMm,
            int widthMm,
            int heightMm,
            int grossWeightGrams,
            Instant now) {
        if (product == null) {
            throw new IllegalArgumentException("SKU requires a product");
        }
        if (this.product == null
                && (!product.isActive() || !product.getProducer().isActive())) {
            throw new IllegalArgumentException("New SKU requires an active product and producer");
        }
        if (this.product != null && !this.product.getId().equals(product.getId())) {
            throw new IllegalArgumentException("SKU cannot be reassigned to a different product");
        }
        if (skuCode == null
                || skuCode.trim().length() > 80
                || !skuCode.trim().matches("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")) {
            throw new IllegalArgumentException("SKU code has an invalid format");
        }
        if (salesUnit == null || salesUnit.isBlank() || salesUnit.length() > 40) {
            throw new IllegalArgumentException("SKU sales unit is required");
        }
        if (productCategory != null && productCategory != product.getCategory()) {
            throw new IllegalArgumentException("SKU product category is immutable");
        }
        if (product.getCategory() == Product.Category.FOOD) {
            if (netContentGrams == null || netContentGrams <= 0) {
                throw new IllegalArgumentException("Food SKU requires positive net content in grams");
            }
            if (minimumShelfLifeDays == null || minimumShelfLifeDays <= 0) {
                throw new IllegalArgumentException("Food SKU requires positive minimum shelf life in days");
            }
            if (fragile) {
                throw new IllegalArgumentException("Food SKU is not marked as fragile in this catalog");
            }
        } else if (netContentGrams != null || minimumShelfLifeDays != null) {
            throw new IllegalArgumentException("Craft SKU cannot have food shelf-life fields");
        }
        if (lengthMm <= 0 || widthMm <= 0 || heightMm <= 0 || grossWeightGrams <= 0) {
            throw new IllegalArgumentException("Gross package dimensions and weight must be positive");
        }
        if (now == null) {
            throw new IllegalArgumentException("Update time is required");
        }
        this.product = product;
        this.productCategory = product.getCategory();
        this.skuCode = skuCode.trim().toUpperCase(Locale.ROOT);
        this.salesUnit = salesUnit.trim();
        this.netContentGrams = netContentGrams;
        this.minimumShelfLifeDays = minimumShelfLifeDays;
        this.fragile = fragile;
        this.lengthMm = lengthMm;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.grossWeightGrams = grossWeightGrams;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public String getSalesUnit() {
        return salesUnit;
    }

    public Integer getNetContentGrams() {
        return netContentGrams;
    }

    public Integer getMinimumShelfLifeDays() {
        return minimumShelfLifeDays;
    }

    public boolean isFragile() {
        return fragile;
    }

    public int getLengthMm() {
        return lengthMm;
    }

    public int getWidthMm() {
        return widthMm;
    }

    public int getHeightMm() {
        return heightMm;
    }

    public int getGrossWeightGrams() {
        return grossWeightGrams;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Update time is required");
        }
        this.active = active;
        this.updatedAt = now;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
