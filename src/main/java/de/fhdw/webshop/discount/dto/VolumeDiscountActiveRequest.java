package de.fhdw.webshop.discount.dto;

import jakarta.validation.constraints.NotNull;

public record VolumeDiscountActiveRequest(
        @NotNull Boolean value
) {
}
