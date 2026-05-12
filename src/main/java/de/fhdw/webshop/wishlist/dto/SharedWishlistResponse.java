package de.fhdw.webshop.wishlist.dto;

import java.util.List;

public record SharedWishlistResponse(
        String id,
        String name,
        String createdAt,
        Boolean shared,
        String shareToken,
        String sharedAt,
        String ownerName,
        List<WishlistItemDto> items
) {
}
