package de.fhdw.webshop.product.dto;

import jakarta.validation.constraints.NotNull;

public record SetFlagRequest(
        @NotNull Boolean value
) {}
