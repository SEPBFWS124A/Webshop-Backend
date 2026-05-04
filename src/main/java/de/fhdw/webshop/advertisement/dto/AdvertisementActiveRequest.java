package de.fhdw.webshop.advertisement.dto;

import jakarta.validation.constraints.NotNull;

public record AdvertisementActiveRequest(@NotNull Boolean value) {
}
