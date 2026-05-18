package de.fhdw.webshop.support.dto;

import de.fhdw.webshop.support.SupportTicketStatus;

import java.time.Instant;
import java.util.List;

public record SupportTicketResponse(
        Long id,
        String ticketNumber,
        String subject,
        SupportTicketStatus status,
        Long customerId,
        String customerName,
        String customerEmail,
        Long relatedOrderId,
        String relatedOrderNumber,
        Instant createdAt,
        Instant updatedAt,
        List<SupportTicketMessageResponse> messages
) {
}
