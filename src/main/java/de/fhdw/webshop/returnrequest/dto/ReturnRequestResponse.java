package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import de.fhdw.webshop.returnrequest.ReturnRequestStatus;
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
        String defectDescription,
        List<ReturnRequestImageResponse> defectImages,
        List<ReturnRequestItemResponse> items,
        ReturnShippingLabelResponse shippingLabel
) {}
