package de.fhdw.webshop.productbundle.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductBundleResponse(
        Long id,
        String title,
        String description,
        String imageUrl,
        BigDecimal discountPercent,
        boolean active,
        boolean featured,
        boolean available,
        Integer totalItemCount,
        BigDecimal originalTotalPrice,
        BigDecimal bundleTotalPrice,
        BigDecimal savingsAmount,
        Instant createdAt,
        Instant updatedAt,
        List<ProductBundleItemResponse> items
) {}
