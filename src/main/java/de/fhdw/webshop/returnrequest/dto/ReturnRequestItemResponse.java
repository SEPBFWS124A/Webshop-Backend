package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import java.math.BigDecimal;

public record ReturnRequestItemResponse(
        Long id,
        Long orderItemId,
        String productName,
        int quantity,
        ReturnReason reason,
        String customerComment,
        BigDecimal lineRefundAmount
) {}
