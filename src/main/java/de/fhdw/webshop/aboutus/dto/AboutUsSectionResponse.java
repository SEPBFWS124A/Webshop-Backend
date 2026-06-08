package de.fhdw.webshop.aboutus.dto;

import java.time.Instant;
import java.util.List;

public record AboutUsSectionResponse(
        Long id,
        String title,
        String content,
        int displayOrder,
        String layoutType,
        Instant createdAt,
        Instant updatedAt,
        List<AboutUsImageResponse> images
) {}
