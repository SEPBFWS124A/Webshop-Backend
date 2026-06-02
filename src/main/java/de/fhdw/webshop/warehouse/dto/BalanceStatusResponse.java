package de.fhdw.webshop.warehouse.dto;

import java.util.List;

public record BalanceStatusResponse(
        boolean balanced,
        int warehouseCount,
        List<ProductImbalance> imbalances
) {}

