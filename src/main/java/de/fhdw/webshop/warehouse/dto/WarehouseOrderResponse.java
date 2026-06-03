package de.fhdw.webshop.warehouse.dto;

import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.order.ShippingMethod;
import java.time.Instant;
import java.util.List;

public record WarehouseOrderResponse(
        Long id,
        Long orderId,
        String orderNumber,
        String customerName,
        String customerEmail,
        OrderStatus status,
        boolean plusMember,
        String regionKey,
        String regionLabel,
        String truckIdentifier,
        String suggestedTruckIdentifier,
        WarehouseLocationResponse fulfillmentWarehouse,
        boolean warehouseStockAvailable,
        List<String> warehouseWarnings,
        ShippingMethod shippingMethod,
        boolean clickAndCollect,
        String deliveryAddress,
        String deliveryStreet,
        String deliveryCity,
        String deliveryPostalCode,
        String deliveryCountry,
        Instant createdAt,
        Instant updatedAt,
        List<WarehouseOrderItemResponse> items
) {}
