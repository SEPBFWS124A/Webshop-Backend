package de.fhdw.webshop.pricehistory;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_price_history", indexes = {
        @Index(name = "idx_pph_product_date", columnList = "product_id, changed_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class ProductPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice; // null beim initialen Eintrag

    @Column(name = "new_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "change_reason", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PriceChangeReason changeReason = PriceChangeReason.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = true)
    private User changedBy; // null bei Systemänderungen

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void prePersist() {
        this.changedAt = Instant.now();
    }
}
