package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateStockRequest(
        @PositiveOrZero int stock
) {}
