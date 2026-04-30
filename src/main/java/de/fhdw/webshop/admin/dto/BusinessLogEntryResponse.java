package de.fhdw.webshop.admin.dto;

import de.fhdw.webshop.admin.AuditInitiator;

import java.time.Instant;

public record BusinessLogEntryResponse(
        Long id,
        Instant timestamp,
        Long userId,
        String username,
        String userEmail,
        String action,
        String entityType,
        Long entityId,
        AuditInitiator initiatedBy,
        String details
) {
}
