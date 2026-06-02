package de.fhdw.webshop.product.dto;

import java.time.LocalDateTime;

public record ProductFeedbackDto(
        Long id,
        String productTitle,
        String userName,
        Integer rating,
        String comment,
        LocalDateTime createdAt,
        String source
) {}
