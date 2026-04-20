package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.discount.dto.CouponResponse;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.user.dto.UserProfileResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CustomerDashboardResponse(
        UserProfileResponse customer,
        Map<String, Object> businessInfo,
        boolean businessCustomer,
        CartResponse cart,
        List<OrderResponse> recentOrders,
        long totalOrderCount,
        Instant latestOrderAt,
        List<DiscountResponse> discounts,
        List<CouponResponse> coupons,
        RevenueStatisticsResponse revenue,
        LocalDate revenueFrom,
        LocalDate revenueTo,
        boolean canViewSalesData,
        boolean canManageSalesActions,
        List<String> alerts,
        String behaviorSummary
) {}
