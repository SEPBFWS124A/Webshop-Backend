package de.fhdw.webshop.quote.dto;

import jakarta.validation.constraints.Size;

public record CreateQuoteRequest(
        @Size(max = 2000) String notes
) {}
