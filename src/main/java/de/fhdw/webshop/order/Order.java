package de.fhdw.webshop.order;

import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(name = "delivery_street", length = 255)
    private String deliveryStreet;

    @Column(name = "delivery_city", length = 100)
    private String deliveryCity;

    @Column(name = "delivery_postal_code", length = 20)
    private String deliveryPostalCode;

    @Column(name = "delivery_country", length = 100)
    private String deliveryCountry;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "payment_method_type", columnDefinition = "payment_method_type")
    private de.fhdw.webshop.user.PaymentMethodType paymentMethodType;

    @Column(name = "payment_masked_details", length = 255)
    private String paymentMaskedDetails;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "climate_contribution_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal climateContributionAmount = BigDecimal.ZERO;

    @Column(name = "carbon_compensation_selected", nullable = false)
    private boolean carbonCompensationSelected = false;

    @Column(name = "total_co2_emission_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalCo2EmissionKg = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", nullable = false, length = 30)
    private ShippingMethod shippingMethod = ShippingMethod.STANDARD;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "order_status")
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "approval_reason", length = 1000)
    private String approvalReason;

    @Column(name = "approval_budget_limit", precision = 12, scale = 2)
    private BigDecimal approvalBudgetLimit;

    @Column(name = "truck_identifier", length = 50)
    private String truckIdentifier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
}
