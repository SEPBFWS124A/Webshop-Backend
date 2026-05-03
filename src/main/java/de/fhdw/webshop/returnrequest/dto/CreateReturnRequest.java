package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateReturnRequest(
        @NotNull Long orderId,
        @NotNull ReturnReason reason,
        @NotEmpty List<@NotNull Long> orderItemIds,
        @Size(max = 500) String defectDescription,
        @Size(max = 3) List<@Valid ReturnRequestImageUpload> defectImages
) {
    public CreateReturnRequest {
        if (defectImages == null) {
            defectImages = List.of();
        }
    }
}
