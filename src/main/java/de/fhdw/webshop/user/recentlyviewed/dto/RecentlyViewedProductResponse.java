package de.fhdw.webshop.user.recentlyviewed.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record RecentlyViewedProductResponse(
        Long productId,
        String productName,
        String description,
        String imageUrl,
        String category,
        BigDecimal recommendedRetailPrice,
        boolean purchasable,
        String sellerName,
        Instant viewedAt
) {
}
