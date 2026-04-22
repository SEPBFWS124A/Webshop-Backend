package de.fhdw.webshop.recommendation.dto;

import java.util.List;

public record ProductRecommendationListResponse(
        List<ProductRecommendationItemResponse> recommendations,
        boolean personalized,
        boolean fallbackUsed
) {}
