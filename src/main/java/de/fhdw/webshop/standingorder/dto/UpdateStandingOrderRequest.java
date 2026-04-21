package de.fhdw.webshop.standingorder.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.List;

public record UpdateStandingOrderRequest(
        @Min(1) int intervalDays,
        LocalDate nextExecutionDate,
        List<StandingOrderItemRequest> items
) {}
