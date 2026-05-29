package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.order.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "return_request_items")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private ReturnRequest returnRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReturnReason reason = ReturnReason.OTHER;

    @Column(name = "customer_comment", length = 500)
    private String customerComment;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "original_total_price", precision = 10, scale = 2)
    private java.math.BigDecimal originalTotalPrice = java.math.BigDecimal.ZERO;

    @Column(name = "discount_share", precision = 10, scale = 2)
    private java.math.BigDecimal discountShare = java.math.BigDecimal.ZERO;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private java.math.BigDecimal refundAmount = java.math.BigDecimal.ZERO;

}
