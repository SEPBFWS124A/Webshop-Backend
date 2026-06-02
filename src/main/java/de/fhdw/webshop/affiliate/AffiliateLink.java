package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "affiliate_links")
@Getter
@Setter
@NoArgsConstructor
public class AffiliateLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "affiliate_profile_id", nullable = false)
    private AffiliateProfile affiliateProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 12)
    private String trackingCode;

    @Column(name = "click_count", nullable = false)
    private int clickCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
