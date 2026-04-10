package de.fhdw.webshop.standingorder.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StandingOrderResponse(
        Long id,
        int intervalDays,
        LocalDate nextExecutionDate,
        boolean active,
        Instant createdAt,
        List<StandingOrderItemResponse> items
) {}
