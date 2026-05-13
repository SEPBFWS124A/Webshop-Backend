package de.fhdw.webshop.sellerreview;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "seller_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seller_reviews_order_seller",
                columnNames = {"order_id", "seller_name"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class SellerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "seller_name", nullable = false, length = 180)
    private String sellerName;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
