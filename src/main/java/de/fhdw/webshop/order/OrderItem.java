package de.fhdw.webshop.order;

import de.fhdw.webshop.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "seller_name", nullable = false, length = 180)
    private String sellerName = "Webshop";

    @Column(nullable = false)
    private int quantity;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Column(name = "picked_by_user_id", length = 100)
    private String pickedByUserId;

    @Column(name = "picked_quantity")
    private Integer pickedQuantity;

    /** Price captured at the moment the order was placed — immune to future price changes. */
    @Column(name = "price_at_order_time", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtOrderTime;

    @Column(name = "personalization_text", length = 1000)
    private String personalizationText;

    @Column(name = "gift_card_code", length = 40)
    private String giftCardCode;

    @Column(name = "gift_card_amount", precision = 10, scale = 2)
    private BigDecimal giftCardAmount;

    @Column(name = "gift_card_recipient_email", length = 255)
    private String giftCardRecipientEmail;

    @Column(name = "gift_card_message", length = 1000)
    private String giftCardMessage;

    @Column(name = "gift_card_redeemed_at")
    private Instant giftCardRedeemedAt;

    @Column(name = "gift_card_redeemed_order_number", length = 30)
    private String giftCardRedeemedOrderNumber;

    @Column(name = "shared_wishlist_token", length = 80)
    private String sharedWishlistToken;

    @Column(name = "shared_wishlist_list_id", length = 140)
    private String sharedWishlistListId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bundle_id")
    private de.fhdw.webshop.productbundle.ProductBundle bundle;

    @Column(name = "bundle_title", length = 180)
    private String bundleTitle;

    @Column(name = "bundle_group_key", length = 80)
    private String bundleGroupKey;

    @Column(name = "bundle_discount_percent", precision = 5, scale = 2)
    private BigDecimal bundleDiscountPercent;
}
