package de.fhdw.webshop.wishlist.dto;

import java.util.List;

public record WishlistStateDto(
        List<WishlistListDto> lists,
        String activeListId,
        List<WishlistItemDto> items
) {
}
