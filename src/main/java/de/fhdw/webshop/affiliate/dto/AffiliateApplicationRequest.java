package de.fhdw.webshop.affiliate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AffiliateApplicationRequest(
        @NotBlank
        @Size(min = 50, message = "Der Motivationstext muss mindestens 50 Zeichen lang sein.")
        String motivationText
) {}
