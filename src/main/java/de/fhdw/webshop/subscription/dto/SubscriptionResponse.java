package de.fhdw.webshop.subscription.dto;

import de.fhdw.webshop.subscription.SubscriptionPlan;
import de.fhdw.webshop.subscription.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        SubscriptionPlan plan,
        SubscriptionStatus status,
        BigDecimal monthlyPrice,
        Instant startedAt,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant cancelledAt
) {}
