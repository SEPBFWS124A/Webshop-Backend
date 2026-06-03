package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "affiliate_profiles")
@Getter
@Setter
@NoArgsConstructor
public class AffiliateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate = new BigDecimal("0.0100");

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private AffiliateTier tier = AffiliateTier.TIER_3;

    @Column(name = "total_earnings_confirmed", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEarningsConfirmed = BigDecimal.ZERO;

    @Column(name = "pending_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal pendingEarnings = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
