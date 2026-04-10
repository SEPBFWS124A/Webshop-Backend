package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Recurring order that fires automatically every intervalDays days (US #51–#53, #55). */
@Entity
@Table(name = "standing_orders")
@Getter
@Setter
@NoArgsConstructor
public class StandingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "standingOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StandingOrderItem> items = new ArrayList<>();
}
