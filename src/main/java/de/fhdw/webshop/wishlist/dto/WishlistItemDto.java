package de.fhdw.webshop.wishlist.dto;

import java.math.BigDecimal;

public record WishlistItemDto(
        String listId,
        Long productId,
        String name,
        String description,
        String category,
        String imageUrl,
        BigDecimal recommendedRetailPrice,
        Boolean purchasable,
        Boolean promoted,
        Integer stock,
        Integer inventory,
        String savedAt,
        String note,
        Integer desiredQuantity,
        Integer purchasedQuantity
) {
}
