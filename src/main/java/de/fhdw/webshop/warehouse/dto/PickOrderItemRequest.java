package de.fhdw.webshop.warehouse.dto;

import jakarta.validation.constraints.Min;

public record PickOrderItemRequest(
		boolean picked,
		@Min(1) Integer pickedQuantity
) {}

