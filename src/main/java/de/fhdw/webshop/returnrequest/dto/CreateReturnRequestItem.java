package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReturnRequestItem(
        @NotNull Long orderItemId,
        @Min(1) int quantity,
        @NotNull ReturnReason reason,
        @Size(max = 500) String comment
) {}
