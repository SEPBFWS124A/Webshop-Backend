package de.fhdw.webshop.warehouse;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderItemResponse;
import de.fhdw.webshop.warehouse.dto.WarehouseOrderResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private static final List<OrderStatus> ACTIVE_WAREHOUSE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED_IN_WAREHOUSE,
            OrderStatus.IN_TRUCK,
            OrderStatus.SHIPPED
    );

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<WarehouseOrderResponse> listOrders(OrderStatus status) {
        List<Order> orders = status == null
                ? orderRepository.findByStatusNotInOrderByCreatedAtAsc(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
                : orderRepository.findByStatusOrderByCreatedAtAsc(status);

        return orders.stream()
                .filter(order -> status != null || ACTIVE_WAREHOUSE_STATUSES.contains(order.getStatus()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WarehouseOrderResponse advanceOrder(Long orderId, String requestedTruckIdentifier) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        OrderStatus nextStatus = determineNextStatus(order.getStatus());
        if (nextStatus == null) {
            throw new IllegalArgumentException("Order status can no longer be advanced");
        }

        if (nextStatus == OrderStatus.IN_TRUCK) {
            String resolvedTruckIdentifier = normalizeTruckIdentifier(
                    requestedTruckIdentifier,
                    order.getTruckIdentifier()
            );
            if (resolvedTruckIdentifier == null) {
                throw new IllegalArgumentException("A truck identifier is required before the order can be moved into the truck");
            }
            order.setTruckIdentifier(resolvedTruckIdentifier);
        }

        order.setStatus(nextStatus);
        return toResponse(orderRepository.save(order));
    }

    private OrderStatus determineNextStatus(OrderStatus currentStatus) {
        return switch (currentStatus) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PACKED_IN_WAREHOUSE;
            case PACKED_IN_WAREHOUSE -> OrderStatus.IN_TRUCK;
            case IN_TRUCK -> OrderStatus.SHIPPED;
            case SHIPPED -> OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> null;
        };
    }

    private String normalizeTruckIdentifier(String requestedTruckIdentifier, String existingTruckIdentifier) {
        if (requestedTruckIdentifier != null && !requestedTruckIdentifier.isBlank()) {
            return requestedTruckIdentifier.trim();
        }
        if (existingTruckIdentifier != null && !existingTruckIdentifier.isBlank()) {
            return existingTruckIdentifier.trim();
        }
        return null;
    }

    private WarehouseOrderResponse toResponse(Order order) {
        List<WarehouseOrderItemResponse> items = order.getItems().stream()
                .map(orderItem -> new WarehouseOrderItemResponse(
                        orderItem.getProduct().getId(),
                        orderItem.getProduct().getName(),
                        orderItem.getQuantity(),
                        orderItem.getProduct().getStock(),
                        orderItem.getProduct().getWarehousePosition()
                ))
                .toList();

        return new WarehouseOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getStatus(),
                order.getTruckIdentifier(),
                order.getCreatedAt(),
                items
        );
    }
}
