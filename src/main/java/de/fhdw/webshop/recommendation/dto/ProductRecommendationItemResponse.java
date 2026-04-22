package de.fhdw.webshop.recommendation.dto;

import de.fhdw.webshop.product.dto.ProductResponse;

public record ProductRecommendationItemResponse(
        ProductResponse product,
        String reason,
        String strategy
) {}
