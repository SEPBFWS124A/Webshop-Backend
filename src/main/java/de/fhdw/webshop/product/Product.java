package de.fhdw.webshop.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "recommended_retail_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal recommendedRetailPrice;

    @Column(name = "co2_emission_kg", precision = 10, scale = 3)
    private BigDecimal co2EmissionKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "eco_score", nullable = false, length = 20)
    private ProductEcoScore ecoScore = ProductEcoScore.NONE;

    @Column(length = 100)
    private String category;

    @Column(name = "seller_name", nullable = false, length = 180)
    private String sellerName = "Webshop";

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 40)
    private ProductType productType = ProductType.STANDARD;

    @Column(nullable = false)
    private int stock = 25;

    @Column(length = 100)
    private String sku;

    @Column(name = "has_variants", nullable = false)
    private boolean hasVariants = false;

    @Column(nullable = false)
    private boolean personalizable = false;

    @Column(name = "personalization_max_length")
    private Integer personalizationMaxLength;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id")
    private Product parentProduct;

    @OneToMany(mappedBy = "parentProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Product> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductVariantAttribute> variantAttributes = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductVariantOption> variantOptions = new ArrayList<>();

    @Column(name = "warehouse_position", length = 80)
    private String warehousePosition;

    /** When false, the product is hidden from customers but visible to employees (US #8, #10, #15). */
    @Column(nullable = false)
    private boolean purchasable = false;

    /** Promoted products are highlighted on the storefront (US #26). */
    @Column(nullable = false)
    private boolean promoted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
