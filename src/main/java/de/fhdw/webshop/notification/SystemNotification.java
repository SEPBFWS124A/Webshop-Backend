package de.fhdw.webshop.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "system_notifications")
@Getter
@Setter
@NoArgsConstructor
public class SystemNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SystemNotificationType type;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "change_percent", precision = 7, scale = 2)
    private BigDecimal changePercent;

    @Column(name = "current_period_units", nullable = false)
    private long currentPeriodUnits;

    @Column(name = "previous_period_units", nullable = false)
    private long previousPeriodUnits;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
