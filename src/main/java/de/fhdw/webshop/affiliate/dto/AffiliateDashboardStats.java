package de.fhdw.webshop.affiliate.dto;

import java.math.BigDecimal;

public record AffiliateDashboardStats(
        long totalClicks,
        long totalConversions,
        BigDecimal totalRevenue,
        BigDecimal confirmedEarnings,
        BigDecimal pendingEarnings,
        long activeLinksCount,
        String tier,
        BigDecimal nextTierRevenue
) {}
