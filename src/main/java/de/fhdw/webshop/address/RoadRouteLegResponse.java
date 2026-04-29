package de.fhdw.webshop.address;

public record RoadRouteLegResponse(
        int sequence,
        String fromId,
        String fromLabel,
        String toId,
        String toLabel,
        double distanceMeters,
        double durationSeconds
) {}
