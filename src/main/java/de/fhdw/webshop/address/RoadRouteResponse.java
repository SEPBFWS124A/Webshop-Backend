package de.fhdw.webshop.address;

import java.util.List;

public record RoadRouteResponse(
        boolean roundTrip,
        double distanceMeters,
        double durationSeconds,
        List<RoadRoutePointResponse> geometry,
        List<RoadRouteLegResponse> legs
) {}
