package de.fhdw.webshop.advertisement.dto;

import de.fhdw.webshop.advertisement.AdvertisementType;

import java.time.Instant;

public record AdvertisementResponse(
        Long id,
        String title,
        String description,
        AdvertisementType contentType,
        String imageUrl,
        String targetUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
