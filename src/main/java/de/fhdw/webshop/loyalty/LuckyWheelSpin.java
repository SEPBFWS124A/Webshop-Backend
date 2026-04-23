package de.fhdw.webshop.loyalty;

import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Protokoll jedes Glücksrad-Spins eines Nutzers (max. 1 pro 24 h). */
@Entity
@Table(name = "lucky_wheel_spins")
@Getter
@Setter
@NoArgsConstructor
public class LuckyWheelSpin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prize_id")
    private LuckyWheelPrize prize;

    @Column(nullable = false)
    private boolean won = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "spun_at", nullable = false, updatable = false)
    private Instant spunAt = Instant.now();
}
