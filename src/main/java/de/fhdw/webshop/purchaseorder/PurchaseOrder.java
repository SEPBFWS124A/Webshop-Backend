package de.fhdw.webshop.purchaseorder;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.WarehouseLocation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.ORDERED;

    @Column(name = "ai_suggested_quantity")
    private Integer aiSuggestedQuantity;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt = Instant.now();

    @Column(name = "received_at")
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ordered_by_user_id")
    private User orderedByUser;

    /** #138/#139 — Target warehouse where goods will be received. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_location_id")
    private WarehouseLocation warehouseLocation;
}
