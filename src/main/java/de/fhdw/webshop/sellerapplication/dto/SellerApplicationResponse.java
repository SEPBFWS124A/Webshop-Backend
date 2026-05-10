package de.fhdw.webshop.sellerapplication.dto;

import de.fhdw.webshop.sellerapplication.SellerApplicationStatus;

import java.time.Instant;

public record SellerApplicationResponse(
        Long id,
        String companyName,
        String contactName,
        String email,
        String phone,
        String website,
        String productCategory,
        String message,
        SellerApplicationStatus status,
        Instant createdAt
) {
}
