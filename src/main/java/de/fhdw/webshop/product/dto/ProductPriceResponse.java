package de.fhdw.webshop.product.dto;

import java.math.BigDecimal;

/**
 * Returned by GET /api/products/{id}/price-for-customer (US #31).
 * Shows the effective price after applying all active discounts for the requesting customer.
 */
public record ProductPriceResponse(
        Long productId,
        BigDecimal recommendedRetailPrice,
        BigDecimal effectivePrice,
        BigDecimal discountPercent
) {}
