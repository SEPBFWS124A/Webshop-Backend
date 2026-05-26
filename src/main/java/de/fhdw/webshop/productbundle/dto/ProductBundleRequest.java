package de.fhdw.webshop.productbundle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ProductBundleRequest(
        @NotBlank String title,
        String description,
        String imageUrl,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent,
        @NotNull Boolean active,
        @NotNull Boolean featured,
        @NotEmpty List<@Valid ProductBundleItemRequest> items
) {}
