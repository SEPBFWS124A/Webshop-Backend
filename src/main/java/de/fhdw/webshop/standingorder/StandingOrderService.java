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
import java.time.YearMonth;
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

    @Transactional
    public StandingOrderResponse create(User customer, CreateStandingOrderRequest request) {
        validateIntervalFields(request.intervalType(), request.intervalDays(), 
                               request.dayOfWeek(), request.dayOfMonth(), request.monthOfYear());

        StandingOrder standingOrder = new StandingOrder();
        standingOrder.setCustomer(customer);
        standingOrder.setIntervalType(request.intervalType());
        standingOrder.setIntervalDays(request.intervalDays());
        standingOrder.setDayOfWeek(request.dayOfWeek());
        standingOrder.setDayOfMonth(request.dayOfMonth());
        standingOrder.setMonthOfYear(request.monthOfYear());

        LocalDate firstExecution = calculateFirstExecutionDate(
                request.intervalType(), request.firstExecutionDate(),
                request.dayOfWeek(), request.dayOfMonth(), request.monthOfYear());
        standingOrder.setNextExecutionDate(firstExecution);

        applyItems(standingOrder, request.items());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    @Transactional
    public StandingOrderResponse update(Long standingOrderId, Long customerId, UpdateStandingOrderRequest request) {
        validateIntervalFields(request.intervalType(), request.intervalDays(), 
                               request.dayOfWeek(), request.dayOfMonth(), request.monthOfYear());

        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setIntervalType(request.intervalType());
        standingOrder.setIntervalDays(request.intervalDays());
        standingOrder.setDayOfWeek(request.dayOfWeek());
        standingOrder.setDayOfMonth(request.dayOfMonth());
        standingOrder.setMonthOfYear(request.monthOfYear());

        standingOrder.getItems().clear();
        applyItems(standingOrder, request.items());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    @Transactional
    public void delete(Long standingOrderId, Long customerId) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrderRepository.delete(standingOrder);
    }

    @Transactional
    public StandingOrderResponse toggleActive(Long standingOrderId, Long customerId) {
        StandingOrder standingOrder = loadForCustomer(standingOrderId, customerId);
        standingOrder.setActive(!standingOrder.isActive());
        return toResponse(standingOrderRepository.save(standingOrder));
    }

    @Transactional
    public void executeAllDue() {
        List<StandingOrder> dueOrders = standingOrderRepository
                .findByActiveIsTrueAndNextExecutionDateLessThanEqual(LocalDate.now());

        for (StandingOrder standingOrder : dueOrders) {
            executeStandingOrder(standingOrder);
            standingOrder.setNextExecutionDate(calculateNextExecutionDate(standingOrder));
            standingOrderRepository.save(standingOrder);
        }
    }

    private LocalDate calculateNextExecutionDate(StandingOrder order) {
        LocalDate current = order.getNextExecutionDate();
        return switch (order.getIntervalType()) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusDays(7);
            case MONTHLY -> calculateNextMonthDay(current, order.getDayOfMonth());
            case YEARLY -> calculateNextYearDate(current, order.getDayOfMonth(), order.getMonthOfYear());
            case DAYS -> current.plusDays(order.getIntervalDays());
        };
    }

    private LocalDate calculateFirstExecutionDate(IntervalType type, LocalDate start, Integer dow, Integer dom, Integer moy) {
        return switch (type) {
            case DAILY, DAYS -> start;
            case WEEKLY -> {
                int currentDow = start.getDayOfWeek().getValue();
                int daysToAdd = (dow - currentDow + 7) % 7;
                yield start.plusDays(daysToAdd);
            }
            case MONTHLY -> calculateFirstMonthDay(start, dom);
            case YEARLY -> calculateFirstYearDate(start, dom, moy);
        };
    }

    private LocalDate calculateNextMonthDay(LocalDate from, Integer dom) {
        LocalDate next = from.plusMonths(1);
        return getValidDateForMonth(next.getYear(), next.getMonthValue(), dom);
    }

    private LocalDate calculateFirstMonthDay(LocalDate start, Integer dom) {
        LocalDate thisMonth = getValidDateForMonth(start.getYear(), start.getMonthValue(), dom);
        return thisMonth.isBefore(start) ? calculateNextMonthDay(start, dom) : thisMonth;
    }

    private LocalDate calculateFirstYearDate(LocalDate start, Integer dom, Integer moy) {
        LocalDate thisYear = getValidDateForMonth(start.getYear(), moy, dom);
        return thisYear.isBefore(start) ? getValidDateForMonth(start.getYear() + 1, moy, dom) : thisYear;
    }

    private LocalDate calculateNextYearDate(LocalDate from, Integer dom, Integer moy) {
        return getValidDateForMonth(from.getYear() + 1, moy, dom);
    }

    private LocalDate getValidDateForMonth(int year, int month, int dom) {
        YearMonth ym = YearMonth.of(year, month);
        if (dom > ym.lengthOfMonth()) {
            return LocalDate.of(year, month, 1).plusMonths(1);
        }
        return LocalDate.of(year, month, dom);
    }

    private void validateIntervalFields(IntervalType type, Integer days, Integer dow, Integer dom, Integer moy) {
        if (type == IntervalType.WEEKLY && dow == null) throw new IllegalArgumentException("Wochentag fehlt");
        if (type == IntervalType.MONTHLY && dom == null) throw new IllegalArgumentException("Tag fehlt");
        if (type == IntervalType.YEARLY && (dom == null || moy == null)) throw new IllegalArgumentException("Datum fehlt");
        if (type == IntervalType.DAYS && days == null) throw new IllegalArgumentException("Intervall fehlt");
    }

    private void executeStandingOrder(StandingOrder standingOrder) {
        User customer = standingOrder.getCustomer();
        Order order = new Order();
        order.setCustomer(customer);
        BigDecimal subtotal = BigDecimal.ZERO;

        for (StandingOrderItem standingOrderItem : standingOrder.getItems()) {
            Product product = standingOrderItem.getProduct();
            if (!product.isPurchasable()) continue;

            BigDecimal unitPrice = product.getRecommendedRetailPrice();
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(standingOrderItem.getQuantity());
            orderItem.setPriceAtOrderTime(unitPrice);
            order.getItems().add(orderItem);
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(standingOrderItem.getQuantity())));
        }

        if (order.getItems().isEmpty()) return;

        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setTotalPrice(subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP));
        order.setTaxAmount(taxAmount);
        order.setShippingCost(BigDecimal.ZERO);
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    private void applyItems(StandingOrder standingOrder, List<StandingOrderItemRequest> itemRequests) {
        for (StandingOrderItemRequest req : itemRequests) {
            StandingOrderItem item = new StandingOrderItem();
            item.setStandingOrder(standingOrder);
            item.setProduct(productService.loadProduct(req.productId()));
            item.setQuantity(req.quantity());
            standingOrder.getItems().add(item);
        }
    }

    private StandingOrder loadForCustomer(Long id, Long customerId) {
        return standingOrderRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    }

    private StandingOrderResponse toResponse(StandingOrder order) {
        List<StandingOrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new StandingOrderItemResponse(i.getId(), i.getProduct().getId(), i.getProduct().getName(), i.getQuantity()))
                .toList();
        return new StandingOrderResponse(
                order.getId(), order.getIntervalType(), order.getIntervalDays(),
                order.getDayOfWeek(), order.getDayOfMonth(), order.getMonthOfYear(),
                order.getNextExecutionDate(), order.isActive(), order.getCreatedAt(), itemResponses);
    }
}