package de.fhdw.webshop.support.dto;

import de.fhdw.webshop.support.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSupportTicketStatusRequest(
        @NotNull SupportTicketStatus status
) {
}
