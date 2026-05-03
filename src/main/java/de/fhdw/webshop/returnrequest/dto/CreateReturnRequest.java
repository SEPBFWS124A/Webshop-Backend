package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateReturnRequest(
        @NotNull Long orderId,
        @NotNull ReturnReason reason,
        @NotEmpty List<@NotNull Long> orderItemIds
) {}
