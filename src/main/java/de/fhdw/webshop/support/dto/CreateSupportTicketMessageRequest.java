package de.fhdw.webshop.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketMessageRequest(
        @NotBlank @Size(max = 5000) String message,
        Boolean internalNote
) {
}
