package de.fhdw.webshop.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExternalRefundConfirmationRequest(
        @NotBlank
        @Size(max = 80)
        String reference
) {}
