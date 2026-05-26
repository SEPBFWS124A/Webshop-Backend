package de.fhdw.webshop.returnrequest.dto;

import jakarta.validation.constraints.Size;

public record ReturnStatusDecisionRequest(
        @Size(max = 500) String reason
) {}
