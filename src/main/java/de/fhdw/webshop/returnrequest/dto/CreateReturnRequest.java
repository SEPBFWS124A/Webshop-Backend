package de.fhdw.webshop.returnrequest.dto;

import de.fhdw.webshop.returnrequest.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateReturnRequest(
        @NotNull Long orderId,
        ReturnReason reason,
        List<@NotNull Long> orderItemIds,
        @Size(max = 50) List<@Valid CreateReturnRequestItem> items,
        @Size(max = 500) String defectDescription,
        @Size(max = 3) List<@Valid ReturnRequestImageUpload> defectImages
) {
    public CreateReturnRequest {
        if (orderItemIds == null) {
            orderItemIds = List.of();
        }
        if (items == null) {
            items = List.of();
        }
        if (defectImages == null) {
            defectImages = List.of();
        }
    }
}
