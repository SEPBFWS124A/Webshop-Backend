package de.fhdw.webshop.cart.dto;

import java.util.List;

public record QuickOrderConfirmResponse(
        CartResponse cart,
        int addedLines,
        int addedQuantity,
        List<QuickOrderPreviewRow> skippedRows
) {}
