package de.fhdw.webshop.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_variant_options")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariantOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "attribute_name", nullable = false, length = 100)
    private String attributeName;

    @Column(name = "attribute_value", nullable = false, length = 100)
    private String attributeValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
