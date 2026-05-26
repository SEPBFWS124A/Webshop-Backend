package de.fhdw.webshop.productbundle.dto;

import de.fhdw.webshop.product.ProductEcoScore;
import java.math.BigDecimal;

public record ProductBundleItemResponse(
        Long productId,
        String productName,
        String description,
        String imageUrl,
        String category,
        String sellerName,
        String sku,
        ProductEcoScore ecoScore,
        BigDecimal co2EmissionKg,
        Integer quantity,
        Integer stock,
        Integer availableStock,
        boolean purchasable,
        BigDecimal recommendedRetailPrice,
        BigDecimal discountedUnitPrice,
        BigDecimal lineOriginalTotal,
        BigDecimal lineBundleTotal
) {}
