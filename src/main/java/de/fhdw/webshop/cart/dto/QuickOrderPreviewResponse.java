package de.fhdw.webshop.cart.dto;

import java.util.List;

public record QuickOrderPreviewResponse(
        List<QuickOrderPreviewRow> rows,
        int validRows,
        int errorRows,
        int totalRequestedQuantity
) {}
