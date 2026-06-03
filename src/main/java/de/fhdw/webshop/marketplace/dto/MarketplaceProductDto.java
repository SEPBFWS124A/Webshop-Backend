package de.fhdw.webshop.marketplace.dto;

import de.fhdw.webshop.product.ProductEcoScore;

import java.math.BigDecimal;

public record MarketplaceProductDto(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal price,
        String category,
        String sellerName,
        ProductEcoScore ecoScore,
        int stock,
        boolean purchasable
) {
}
