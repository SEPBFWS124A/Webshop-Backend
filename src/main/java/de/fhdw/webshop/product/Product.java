package de.fhdw.webshop.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

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

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private int stock = 25;

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
