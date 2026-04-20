package de.fhdw.webshop.discount.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CouponResponse(
        Long id,
        Long customerId,
        String code,
        BigDecimal discountPercent,
        LocalDate validUntil,
        boolean used,
        Instant usedAt
) {}
