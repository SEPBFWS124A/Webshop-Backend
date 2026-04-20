package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.order.dto.OrderResponse;
import de.fhdw.webshop.user.dto.UserProfileResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CustomerCockpitResponse(
        UserProfileResponse customer,
        CustomerBusinessInfoResponse businessInfo,
        CartResponse cart,
        List<OrderResponse> recentOrders,
        RevenueStatisticsResponse revenue,
        List<DiscountResponse> discounts,
        List<CustomerCouponResponse> coupons,
        String behaviorSummary,
        List<String> alerts,
        boolean businessCustomer,
        long totalOrderCount,
        Instant latestOrderAt,
        LocalDate revenueFrom,
        LocalDate revenueTo,
        boolean canViewSalesData,
        boolean canManageSalesActions
) {}
