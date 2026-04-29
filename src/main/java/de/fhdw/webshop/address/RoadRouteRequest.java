package de.fhdw.webshop.address;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RoadRouteRequest(
        @NotEmpty List<@Valid RoadRouteStopRequest> stops,
        Boolean returnToOrigin
) {}
