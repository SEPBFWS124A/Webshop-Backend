package de.fhdw.webshop.followuporder;

import de.fhdw.webshop.followuporder.dto.*;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.DeliveryAddress;
import de.fhdw.webshop.user.DeliveryAddressRepository;
import de.fhdw.webshop.user.PaymentMethod;
import de.fhdw.webshop.user.PaymentMethodRepository;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpOrderService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.19");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");

    private final FollowUpOrderRepository repository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final DeliveryAddressRepository deliveryAddressRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final EmailService emailService;

    public List<FollowUpOrderResponse> listForCustomer(Long customerId) {
        return repository.findByCustomerIdOrderByExecutionDateAsc(customerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public FollowUpOrderResponse create(User customer, CreateFollowUpOrderRequest request) {
        FollowUpOrder fuo = new FollowUpOrder();
        fuo.setCustomer(customer);
        fuo.setExecutionDate(request.executionDate());
        fuo.setSourceOrderId(request.sourceOrderId());
        fuo.setStatus(FollowUpOrderStatus.PENDING);

        for (var itemReq : request.items()) {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new EntityNotFoundException("Produkt nicht gefunden: " + itemReq.productId()));
            FollowUpOrderItem item = new FollowUpOrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.quantity());
            item.setFollowUpOrder(fuo);
            fuo.getItems().add(item);
        }

        return mapToResponse(repository.save(fuo));
    }

    @Transactional
    public void cancel(Long id, Long customerId) {
        FollowUpOrder fuo = repository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new EntityNotFoundException("Folgebestellung nicht gefunden"));
        if (fuo.getStatus() != FollowUpOrderStatus.PENDING) {
            throw new IllegalStateException("Nur ausstehende Folgebestellungen können storniert werden.");
        }
        fuo.setStatus(FollowUpOrderStatus.CANCELLED);
        repository.save(fuo);
    }

    @Transactional
    public void executeAllDue() {
        List<FollowUpOrder> due = repository.findByStatusAndExecutionDateLessThanEqual(
                FollowUpOrderStatus.PENDING, LocalDate.now());
        for (FollowUpOrder fuo : due) {
            try {
                executeSingle(fuo);
            } catch (Exception e) {
                log.error("Fehler bei Folgebestellung {}: {}", fuo.getId(), e.getMessage());
            }
        }
    }

    private void executeSingle(FollowUpOrder fuo) {
        User customer = fuo.getCustomer();
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setOrderNumber(buildOrderNumber());
        order.setCustomerEmail(customer.getEmail());
        order.setCustomerName(customer.getUsername());

        deliveryAddressRepository.findFirstByUserId(customer.getId())
                .ifPresent(addr -> applyDeliveryAddress(order, addr));
        paymentMethodRepository.findFirstByUserId(customer.getId())
                .ifPresent(pm -> applyPaymentMethod(order, pm));

        BigDecimal subtotal = BigDecimal.ZERO;
        List<Product> updatedProducts = new ArrayList<>();

        for (FollowUpOrderItem fuoItem : fuo.getItems()) {
            Product product = fuoItem.getProduct();
            if (!product.isPurchasable() || product.getStock() <= 0) continue;
            int quantity = Math.min(fuoItem.getQuantity(), product.getStock());
            if (quantity <= 0) continue;

            BigDecimal price = product.getRecommendedRetailPrice();
            BigDecimal discount = discountLookupPort.findBestActiveDiscountPercent(
                    customer.getId(), product.getId());
            BigDecimal unitPrice = applyDiscount(price, discount);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setPriceAtOrderTime(unitPrice);
            order.getItems().add(orderItem);

            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
            product.setStock(product.getStock() - quantity);
            updatedProducts.add(product);
        }

        if (order.getItems().isEmpty()) {
            fuo.setStatus(FollowUpOrderStatus.EXECUTED);
            repository.save(fuo);
            return;
        }

        BigDecimal shippingCost = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : SHIPPING_COST;
        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        order.setShippingCost(shippingCost);
        order.setTaxAmount(taxAmount);
        order.setTotalPrice(subtotal.add(taxAmount).add(shippingCost).setScale(2, RoundingMode.HALF_UP));

        orderRepository.save(order);
        productRepository.saveAll(updatedProducts);

        fuo.setStatus(FollowUpOrderStatus.EXECUTED);
        repository.save(fuo);

        sendConfirmation(order, fuo);
    }

    private void applyDeliveryAddress(Order order, DeliveryAddress addr) {
        order.setDeliveryStreet(addr.getStreet());
        order.setDeliveryCity(addr.getCity());
        order.setDeliveryPostalCode(addr.getPostalCode());
        order.setDeliveryCountry(addr.getCountry());
    }

    private void applyPaymentMethod(Order order, PaymentMethod pm) {
        order.setPaymentMethodType(pm.getMethodType());
        order.setPaymentMaskedDetails(pm.getMaskedDetails());
    }

    private String buildOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long suffix = Math.abs(System.nanoTime() % 1_000_000);
        return "FOL-" + timestamp + "-" + String.format("%06d", suffix);
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) return price;
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private void sendConfirmation(Order order, FollowUpOrder fuo) {
        StringBuilder body = new StringBuilder()
                .append("Deine Folgebestellung wurde automatisch ausgefuehrt.\n\n")
                .append("Bestellnummer: ").append(order.getOrderNumber()).append('\n')
                .append("Gesamtbetrag: ").append(order.getTotalPrice()).append(" EUR\n\n")
                .append("Artikel:\n");
        for (OrderItem item : order.getItems()) {
            body.append("- ").append(item.getProduct().getName())
                    .append(" x").append(item.getQuantity())
                    .append(" = ").append(item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .append(" EUR\n");
        }
        emailService.sendEmail(order.getCustomerEmail(), "Folgebestellung " + order.getOrderNumber(), body.toString());
    }

    private FollowUpOrderResponse mapToResponse(FollowUpOrder fuo) {
        List<FollowUpOrderItemResponse> items = fuo.getItems().stream()
                .map(i -> new FollowUpOrderItemResponse(
                        i.getId(), i.getProduct().getId(), i.getProduct().getName(), i.getQuantity()))
                .collect(Collectors.toList());
        return new FollowUpOrderResponse(
                fuo.getId(), fuo.getExecutionDate(), fuo.getStatus(),
                fuo.getSourceOrderId(), fuo.getCreatedAt(), items);
    }
}
