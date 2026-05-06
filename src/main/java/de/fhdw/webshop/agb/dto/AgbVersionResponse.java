package de.fhdw.webshop.agb.dto;

import java.time.Instant;

public record AgbVersionResponse(Long id, String agbText, Instant createdAt) {}
