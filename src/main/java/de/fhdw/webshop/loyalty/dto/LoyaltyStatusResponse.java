package de.fhdw.webshop.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vollständiger Loyalty-Status des eingeloggten Kunden. */
public record LoyaltyStatusResponse(
        int currentStreak,
        int targetStreak,
        LocalDate lastLoginDate,
        boolean canSpinToday,
        boolean volumeDiscountUnlocked,
        BigDecimal totalSpending,
        BigDecimal volumeThreshold
) {}
