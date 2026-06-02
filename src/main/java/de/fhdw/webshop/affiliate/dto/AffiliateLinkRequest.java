package de.fhdw.webshop.affiliate.dto;

import jakarta.validation.constraints.NotNull;

public record AffiliateLinkRequest(@NotNull Long productId) {}
