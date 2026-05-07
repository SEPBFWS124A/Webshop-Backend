package de.fhdw.webshop.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WarehouseTransferRequest(
        @NotNull Long productId,
        @NotNull Long fromWarehouseLocationId,
        @NotNull Long toWarehouseLocationId,
        @Min(1) int quantity
) {}
