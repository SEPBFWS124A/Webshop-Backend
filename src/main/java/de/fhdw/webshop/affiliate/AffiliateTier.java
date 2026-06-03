package de.fhdw.webshop.affiliate;

import java.math.BigDecimal;

public enum AffiliateTier {

    TIER_1(new BigDecimal("0.0500"), new BigDecimal("50000")),
    TIER_2(new BigDecimal("0.0250"), new BigDecimal("10000")),
    TIER_3(new BigDecimal("0.0100"), BigDecimal.ZERO);

    private final BigDecimal defaultRate;
    private final BigDecimal minRevenue;

    AffiliateTier(BigDecimal defaultRate, BigDecimal minRevenue) {
        this.defaultRate = defaultRate;
        this.minRevenue = minRevenue;
    }

    public BigDecimal getDefaultRate() {
        return defaultRate;
    }

    public BigDecimal getMinRevenue() {
        return minRevenue;
    }

    /** Derive tier from total generated revenue (sum of purchaseAmounts). */
    public static AffiliateTier fromRevenue(BigDecimal totalRevenue) {
        if (totalRevenue == null) return TIER_3;
        if (totalRevenue.compareTo(TIER_1.minRevenue) >= 0) return TIER_1;
        if (totalRevenue.compareTo(TIER_2.minRevenue) >= 0) return TIER_2;
        return TIER_3;
    }

    /** Derive display tier from a commission rate (used for manual overrides). */
    public static AffiliateTier fromRate(BigDecimal rate) {
        if (rate == null) return TIER_3;
        if (rate.compareTo(TIER_1.defaultRate) >= 0) return TIER_1;
        if (rate.compareTo(TIER_2.defaultRate) >= 0) return TIER_2;
        return TIER_3;
    }
}
