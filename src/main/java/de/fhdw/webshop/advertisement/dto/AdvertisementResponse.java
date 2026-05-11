package de.fhdw.webshop.advertisement.dto;

import de.fhdw.webshop.advertisement.AdvertisementType;

import java.time.Instant;
import java.time.LocalDate;

public record AdvertisementResponse(
        Long id,
        String title,
        String description,
        AdvertisementType contentType,
        String imageUrl,
        String targetUrl,
        boolean active,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentlyVisible,
        Instant createdAt,
        Instant updatedAt
) {
}
