package de.fhdw.webshop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PreferredLanguageRequest(
        @NotBlank
        @Pattern(regexp = "de|en", message = "Unterstützte Sprachen sind de und en.")
        String preferredLanguage
) {}
