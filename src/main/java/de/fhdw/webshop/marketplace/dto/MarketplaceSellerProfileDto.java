package de.fhdw.webshop.marketplace.dto;

import java.util.List;

public record MarketplaceSellerProfileDto(
        String sellerName,
        String displayName,
        double avgRating,
        long reviewCount,
        List<MarketplaceProductDto> products,
        List<MarketplaceReviewDto> recentReviews
) {
}
