package de.fhdw.webshop.loyalty;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Konfigurierbare Gewinntöpfe für das tägliche Glücksrad (US-3). */
@Entity
@Table(name = "lucky_wheel_prizes")
@Getter
@Setter
@NoArgsConstructor
public class LuckyWheelPrize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String label;

    /** NO_WIN | FREE_SHIPPING | COUPON */
    @Column(name = "prize_type", nullable = false, length = 50)
    private String prizeType;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(nullable = false, precision = 7, scale = 6)
    private BigDecimal probability;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
