package de.fhdw.webshop.warehouse.dto;

import java.util.List;

public record AutoBalanceResponse(
        int transfersCreated,
        int warehouseCount,
        List<AutoBalanceTransferDetail> transfers
) {}

