package de.fhdw.webshop.affiliate.dto;

import de.fhdw.webshop.affiliate.AffiliateApplicationStatus;

import java.time.Instant;

public record AffiliateApplicationResponse(
        Long id,
        AffiliateApplicationStatus status,
        Instant createdAt,
        String reviewNote
) {}
