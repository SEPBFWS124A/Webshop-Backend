package de.fhdw.webshop.agb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAgbVersionRequest(
    @NotBlank @Size(min = 10, max = 100_000) String agbText
) {}
