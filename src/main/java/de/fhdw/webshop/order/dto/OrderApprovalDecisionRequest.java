package de.fhdw.webshop.order.dto;

import jakarta.validation.constraints.Size;

public record OrderApprovalDecisionRequest(
        @Size(max = 1000, message = "Die Begruendung darf maximal 1000 Zeichen lang sein")
        String reason
) {}
