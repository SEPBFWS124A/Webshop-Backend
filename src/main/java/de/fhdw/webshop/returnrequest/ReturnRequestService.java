package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestItemResponse;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private static final Duration RETURN_WINDOW = Duration.ofDays(14);

    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestItemRepository returnRequestItemRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ReturnRequestResponse createReturnRequest(User customer, CreateReturnRequest request) {
        Order order = orderRepository.findByIdAndCustomerId(request.orderId(), customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + request.orderId()));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Retouren koennen nur fuer zugestellte Bestellungen angemeldet werden.");
        }

        Instant deliveredAt = resolveDeliveredAt(order);
        if (Instant.now().isAfter(deliveredAt.plus(RETURN_WINDOW))) {
            throw new IllegalStateException("Die Rueckgabefrist von 14 Tagen nach Lieferdatum ist abgelaufen.");
        }

        LinkedHashSet<Long> requestedItemIds = new LinkedHashSet<>(request.orderItemIds());
        if (requestedItemIds.isEmpty()) {
            throw new IllegalArgumentException("Bitte waehle mindestens einen Artikel fuer die Retoure aus.");
        }

        Map<Long, OrderItem> orderItemsById = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        ReturnRequest returnRequest = new ReturnRequest();
        returnRequest.setCustomer(customer);
        returnRequest.setOrder(order);
        returnRequest.setReason(request.reason());

        for (Long orderItemId : requestedItemIds) {
            OrderItem orderItem = orderItemsById.get(orderItemId);
            if (orderItem == null) {
                throw new IllegalArgumentException("Der Artikel gehoert nicht zu dieser Bestellung: " + orderItemId);
            }
            if (returnRequestItemRepository.existsByOrderItemId(orderItemId)) {
                throw new IllegalStateException("Fuer diesen Artikel wurde bereits eine Retoure angemeldet.");
            }

            ReturnRequestItem returnItem = new ReturnRequestItem();
            returnItem.setReturnRequest(returnRequest);
            returnItem.setOrderItem(orderItem);
            returnItem.setProductName(orderItem.getProduct().getName());
            returnItem.setQuantity(orderItem.getQuantity());
            returnRequest.getItems().add(returnItem);
        }

        return toResponse(returnRequestRepository.save(returnRequest));
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listForCustomer(Long customerId) {
        return returnRequestRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Instant resolveDeliveredAt(Order order) {
        if (order.getDeliveredAt() != null) {
            return order.getDeliveredAt();
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt();
        }
        return Instant.EPOCH;
    }

    private ReturnRequestResponse toResponse(ReturnRequest returnRequest) {
        return new ReturnRequestResponse(
                returnRequest.getId(),
                returnRequest.getCustomer().getId(),
                returnRequest.getOrder().getId(),
                returnRequest.getOrder().getOrderNumber(),
                returnRequest.getReason(),
                returnRequest.getStatus(),
                returnRequest.getCreatedAt(),
                returnRequest.getItems().stream()
                        .map(item -> new ReturnRequestItemResponse(
                                item.getId(),
                                item.getOrderItem().getId(),
                                item.getProductName(),
                                item.getQuantity()))
                        .toList()
        );
    }
}
