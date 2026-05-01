package de.fhdw.webshop.product.dto;

import de.fhdw.webshop.product.ProductEcoScore;
import jakarta.validation.constraints.NotNull;

public record UpdateEcoScoreRequest(
        @NotNull ProductEcoScore ecoScore
) {}
