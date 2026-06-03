package de.fhdw.webshop.referral;

import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referral_code_id", nullable = false)
    private ReferralCode referralCode;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_user_id", nullable = false, unique = true)
    private User referredUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_coupon_id")
    private Coupon referrerCoupon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_coupon_id")
    private Coupon referredCoupon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
