package de.fhdw.webshop.followuporder;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "follow_up_orders")
@Getter
@Setter
@NoArgsConstructor
public class FollowUpOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "source_order_id")
    private Long sourceOrderId;

    @Column(name = "execution_date", nullable = false)
    private LocalDate executionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FollowUpOrderStatus status = FollowUpOrderStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "followUpOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FollowUpOrderItem> items = new ArrayList<>();
}
