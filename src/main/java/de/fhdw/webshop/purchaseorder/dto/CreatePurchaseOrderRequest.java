package de.fhdw.webshop.purchaseorder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreatePurchaseOrderRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        String supplierName
) {}
