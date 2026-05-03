package de.fhdw.webshop.returnrequest.dto;

import java.time.Instant;

public record ReturnShippingLabelResponse(
        String carrierName,
        String trackingId,
        String labelPdfUrl,
        String qrCodePayload,
        String qrCodeSvg,
        Instant createdAt,
        ReturnLabelAddressResponse returnCenterAddress,
        ReturnLabelAddressResponse senderAddress
) {}
