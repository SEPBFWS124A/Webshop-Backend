package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "standing_order_items")
@Getter
@Setter
@NoArgsConstructor
public class StandingOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "standing_order_id", nullable = false)
    private StandingOrder standingOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "notified_unavailable", nullable = false)
    private boolean notifiedUnavailable = false;
}
