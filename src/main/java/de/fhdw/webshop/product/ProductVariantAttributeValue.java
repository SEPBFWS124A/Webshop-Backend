package de.fhdw.webshop.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_variant_attribute_values")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariantAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_id", nullable = false)
    private ProductVariantAttribute attribute;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
