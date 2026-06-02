package de.fhdw.webshop.marketplace.dto;

public record MarketplaceSellerDto(
        String sellerName,
        String displayName,
        double avgRating,
        long reviewCount,
        double avgProductRating
) {
}
