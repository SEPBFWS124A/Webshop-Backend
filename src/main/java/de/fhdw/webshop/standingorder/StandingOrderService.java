package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.standingorder.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StandingOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

    private final StandingOrderRepository standingOrderRepository;
    private final ProductService productService;
    private final OrderRepository orderRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;

    public List<StandingOrderResponse> listForCustomer(Long customerId) {
        return standingOrderRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** US #51 — Create a new standing order. */
    @Transactional
    public StandingOrderResponse create(User customer, CreateStandingOrderRequest createStandingOrderRequest) {
        StandingOrder standingOrder = new StandingOrder();
        standingOrder.setCustomer(customer);
        standingOrder.setIntervalDays(createStandingOrderRequest.intervalDays());
        standingOrder.setNextExecutionDate(createStandingOrderRequest.firstExecutionDate());
        applyItems(standingOrder, createStandingOrderRequest.items());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    /** US #53 — Change interval or items of an existing standing order. */
    @Transactional
    public StandingOrderResponse update(Long standingOrderId, Long customerId,
                                        UpdateStandingOrderRequest updateStandingOrderRequest) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setIntervalDays(updateStandingOrderRequest.intervalDays());
        standingOrder.getItems().clear();
        applyItems(standingOrder, updateStandingOrderRequest.items());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    /** Permanently delete a standing order. */
    @Transactional
    public void delete(Long standingOrderId, Long customerId) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrderRepository.delete(standingOrder);
    }

    /** Toggle active/inactive status. */
    @Transactional
    public StandingOrderResponse toggleActive(Long standingOrderId, Long customerId) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setActive(!standingOrder.isActive());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    /** Called by the scheduler: execute all due standing orders and advance their next execution date. */
    @Transactional
    public void executeAllDue() {
        List<StandingOrder> dueOrders = standingOrderRepository
                .findByActiveIsTrueAndNextExecutionDateLessThanEqual(LocalDate.now());

        for (StandingOrder standingOrder : dueOrders) {
            executeStandingOrder(standingOrder);
            standingOrder.setNextExecutionDate(
                    standingOrder.getNextExecutionDate().plusDays(standingOrder.getIntervalDays()));
            standingOrderRepository.save(standingOrder);
        }
    }

    private void executeStandingOrder(StandingOrder standingOrder) {
        User customer = standingOrder.getCustomer();
        Order order = new Order();
        order.setCustomer(customer);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (StandingOrderItem standingOrderItem : standingOrder.getItems()) {
            Product product = standingOrderItem.getProduct();
            if (!product.isPurchasable()) {
                continue;
            }
            BigDecimal discountPercent = discountLookupPort
                    .findBestActiveDiscountPercent(customer.getId(), product.getId());
            BigDecimal unitPrice = applyDiscount(product.getRecommendedRetailPrice(), discountPercent);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(standingOrderItem.getQuantity());
            orderItem.setPriceAtOrderTime(unitPrice);
            order.getItems().add(orderItem);

            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(standingOrderItem.getQuantity())));
        }

        if (order.getItems().isEmpty()) {
            return;
        }

        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setTotalPrice(subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setShippingCost(BigDecimal.ZERO);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    private void applyItems(StandingOrder standingOrder, List<StandingOrderItemRequest> itemRequests) {
        for (StandingOrderItemRequest itemRequest : itemRequests) {
            StandingOrderItem standingOrderItem = new StandingOrderItem();
            standingOrderItem.setStandingOrder(standingOrder);
            standingOrderItem.setProduct(productService.loadProduct(itemRequest.productId()));
            standingOrderItem.setQuantity(itemRequest.quantity());
            standingOrder.getItems().add(standingOrderItem);
        }
    }

    private StandingOrder loadForCustomer(Long standingOrderId, Long customerId) {
        return standingOrderRepository.findByIdAndCustomerId(standingOrderId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Standing order not found: " + standingOrderId));
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private StandingOrderResponse toResponse(StandingOrder standingOrder) {
        List<StandingOrderItemResponse> itemResponses = standingOrder.getItems().stream()
                .map(item -> new StandingOrderItemResponse(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity()))
                .toList();
        return new StandingOrderResponse(
                standingOrder.getId(),
                standingOrder.getIntervalDays(),
                standingOrder.getNextExecutionDate(),
                standingOrder.isActive(),
                standingOrder.getCreatedAt(),
                itemResponses);
    }
}
