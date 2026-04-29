package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.ShippingMethod;
import java.time.Instant;
import java.util.List;

public record WarehouseOrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        String regionKey,
        String regionLabel,
        String truckIdentifier,
        String suggestedTruckIdentifier,
        ShippingMethod shippingMethod,
        String deliveryStreet,
        String deliveryCity,
        String deliveryPostalCode,
        String deliveryCountry,
        Instant createdAt,
        List<WarehouseOrderItemResponse> items
) {}
