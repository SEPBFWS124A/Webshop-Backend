package de.fhdw.webshop.cart.dto;

import java.util.List;

public record QuickOrderPreviewRow(
        int lineNumber,
        String articleNumber,
        Integer requestedQuantity,
        Long productId,
        String productName,
        Integer availableStock,
        boolean valid,
        List<String> errors
) {}
