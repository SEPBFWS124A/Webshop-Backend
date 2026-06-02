package de.fhdw.webshop.affiliate.dto;

import java.math.BigDecimal;

public record AdminAffiliateListItem(
        Long affiliateId,
        Long userId,
        String username,
        String email,
        BigDecimal commissionRate,
        long totalLinks,
        long totalClicks,
        long totalConversions,
        BigDecimal confirmedEarnings,
        BigDecimal pendingEarnings,
        boolean active
) {}
