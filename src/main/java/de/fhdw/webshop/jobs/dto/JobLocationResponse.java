package de.fhdw.webshop.jobs.dto;

import java.time.Instant;

public record JobLocationResponse(
        Long id,
        String name,
        String street,
        String houseNumber,
        String postalCode,
        String city,
        String locationType,
        Double latitude,
        Double longitude,
        Instant createdAt,
        Instant updatedAt
) {}
