package de.fhdw.webshop.cart;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserId(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    Optional<CartItem> findByUserIdAndProductIdAndPersonalizationText(Long userId, Long productId, String personalizationText);

    Optional<CartItem> findByUserIdAndProductIdAndPersonalizationTextAndGiftCardAmountAndGiftCardRecipientEmailAndGiftCardMessage(
            Long userId,
            Long productId,
            String personalizationText,
            java.math.BigDecimal giftCardAmount,
            String giftCardRecipientEmail,
            String giftCardMessage
    );

    void deleteByUserId(Long userId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
