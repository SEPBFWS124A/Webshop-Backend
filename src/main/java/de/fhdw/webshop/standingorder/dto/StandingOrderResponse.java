package de.fhdw.webshop.standingorder.dto;

import de.fhdw.webshop.standingorder.IntervalType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StandingOrderResponse(
        Long id,
        IntervalType intervalType,
        Integer intervalDays,
        Integer dayOfWeek,
        Integer dayOfMonth,
        Integer monthOfYear,
        LocalDate nextExecutionDate,
        boolean active,
        Instant createdAt,
        List<StandingOrderItemResponse> items
) {}