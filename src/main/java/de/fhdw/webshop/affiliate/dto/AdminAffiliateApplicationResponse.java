package de.fhdw.webshop.affiliate.dto;

import de.fhdw.webshop.affiliate.AffiliateApplicationStatus;

import java.time.Instant;

public record AdminAffiliateApplicationResponse(
        Long id,
        Long userId,
        String username,
        String email,
        String motivationText,
        AffiliateApplicationStatus status,
        String reviewedByUsername,
        String reviewNote,
        Instant createdAt
) {}
