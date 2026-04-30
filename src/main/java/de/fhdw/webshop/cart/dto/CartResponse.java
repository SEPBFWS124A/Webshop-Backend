package de.fhdw.webshop.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal tax,
        BigDecimal shippingCost,
        BigDecimal total,
        BigDecimal totalCo2EmissionKg,
        int co2EmissionCoveredItemCount,
        int co2EmissionTotalItemCount,
        String appliedCouponCode,
        List<String> messages
) {}
