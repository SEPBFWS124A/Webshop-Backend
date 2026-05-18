package de.fhdw.webshop.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotBlank @Size(max = 180) String subject,
        @NotBlank @Size(max = 5000) String message,
        Long orderId
) {
}
