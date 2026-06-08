package de.fhdw.webshop.aboutus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAboutUsSectionRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        int displayOrder,
        String layoutType
) {}
