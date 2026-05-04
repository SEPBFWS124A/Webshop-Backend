package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnInspectionCondition;
import de.fhdw.webshop.returnrequest.ReturnReason;
import de.fhdw.webshop.returnrequest.ReturnRefundMethod;
import de.fhdw.webshop.returnrequest.ReturnRefundStatus;
import de.fhdw.webshop.returnrequest.ReturnRequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReturnRequestResponse(
        Long id,
        Long customerId,
        String customerName,
        String customerEmail,
        Long orderId,
        String orderNumber,
        ReturnReason reason,
        ReturnRequestStatus status,
        Instant createdAt,
        Instant inspectedAt,
        ReturnInspectionCondition inspectionCondition,
        ReturnRefundStatus refundStatus,
        ReturnRefundMethod refundMethod,
        BigDecimal refundAmount,
        String refundReference,
        String defectDescription,
        List<ReturnRequestImageResponse> defectImages,
        List<ReturnRequestItemResponse> items,
        ReturnShippingLabelResponse shippingLabel
) {}
