package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnInspectionCondition;
import jakarta.validation.constraints.NotNull;

public record InspectReturnRequest(
        @NotNull ReturnInspectionCondition condition
) {}
