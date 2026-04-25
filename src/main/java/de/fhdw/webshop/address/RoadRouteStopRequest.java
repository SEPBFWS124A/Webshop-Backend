package de.fhdw.webshop.address;

import jakarta.validation.constraints.NotBlank;

public record RoadRouteStopRequest(
        @NotBlank String id,
        @NotBlank String label,
        double latitude,
        double longitude
) {}
