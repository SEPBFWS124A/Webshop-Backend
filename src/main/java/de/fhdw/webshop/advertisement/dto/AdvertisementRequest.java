package de.fhdw.webshop.advertisement.dto;

import de.fhdw.webshop.advertisement.AdvertisementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdvertisementRequest(
        @NotBlank @Size(max = 180) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull AdvertisementType contentType,
        @Size(max = 500) String imageUrl,
        @NotBlank @Size(max = 500) String targetUrl,
        @NotNull Boolean active
) {
}
