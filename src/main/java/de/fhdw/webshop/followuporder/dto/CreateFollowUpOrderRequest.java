package de.fhdw.webshop.followuporder.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateFollowUpOrderRequest(
    @NotNull LocalDate executionDate,
    Long sourceOrderId,
    @NotEmpty List<FollowUpOrderItemRequest> items
) {}
