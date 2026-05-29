package de.fhdw.webshop.sellerportal.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record SellerPayoutCorrectionRequest(
        @NotBlank(message = "Ein Korrekturgrund ist erforderlich.")
        String reason,
        BigDecimal manualAdjustmentAmount,
        String note
) {
}
