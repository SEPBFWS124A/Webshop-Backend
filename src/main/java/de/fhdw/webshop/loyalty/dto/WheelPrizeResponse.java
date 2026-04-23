package de.fhdw.webshop.loyalty.dto;

import java.math.BigDecimal;

public record WheelPrizeResponse(
        Long id,
        String label,
        String prizeType,
        BigDecimal discountPercent,
        BigDecimal probability,
        boolean active
) {}
