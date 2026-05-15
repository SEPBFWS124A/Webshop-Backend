package de.fhdw.webshop.cart;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cart_items")
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "personalization_text", length = 1000)
    private String personalizationText;

    @Column(name = "gift_card_amount", precision = 10, scale = 2)
    private BigDecimal giftCardAmount;

    @Column(name = "gift_card_recipient_email", length = 255)
    private String giftCardRecipientEmail;

    @Column(name = "gift_card_message", length = 1000)
    private String giftCardMessage;

    @Column(name = "shared_wishlist_token", length = 80)
    private String sharedWishlistToken;

    @Column(name = "shared_wishlist_list_id", length = 140)
    private String sharedWishlistListId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();
}
