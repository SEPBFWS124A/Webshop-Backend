package de.fhdw.webshop.sellerportal.dto;

import java.math.BigDecimal;

public record SellerDashboardResponse(
        SellerProfileResponse seller,
        long totalOrders,
        long deliveredOrders,
        long returnRequests,
        BigDecimal grossRevenue,
        BigDecimal netRevenue,
        BigDecimal returnRate,
        long openPayoutCount,
        BigDecimal openPayoutAmount
) {
}
