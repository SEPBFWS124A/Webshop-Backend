package de.fhdw.webshop.tradein;

import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderItemRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.tradein.dto.CreateTradeInRequest;
import de.fhdw.webshop.tradein.dto.TradeInResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TradeInService {

    private static final BigDecimal RATE_LIKE_NEW   = new BigDecimal("0.30");
    private static final BigDecimal RATE_LIGHT_WEAR = new BigDecimal("0.15");
    private static final BigDecimal RATE_DEFECTIVE  = new BigDecimal("0.05");

    private final TradeInRepository tradeInRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponRepository couponRepository;
    private final EmailService emailService;

    public BigDecimal estimateValue(BigDecimal purchasePrice, TradeInCondition condition) {
        BigDecimal rate = switch (condition) {
            case LIKE_NEW   -> RATE_LIKE_NEW;
            case LIGHT_WEAR -> RATE_LIGHT_WEAR;
            case DEFECTIVE  -> RATE_DEFECTIVE;
        };
        return purchasePrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public TradeInResponse createTradeIn(User customer, CreateTradeInRequest req) {
        OrderItem orderItem = findOrderItemForCustomer(req.orderItemId(), customer.getId());
        Order order = orderItem.getOrder();

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalStateException("Trade-In ist nur für zugestellte Bestellungen möglich.");
        }

        boolean alreadyPending = tradeInRepository.existsByOrderItemIdAndStatusNot(
                req.orderItemId(), TradeInStatus.REJECTED);
        if (alreadyPending) {
            throw new IllegalStateException("Für diesen Artikel existiert bereits eine aktive Trade-In-Anfrage.");
        }

        BigDecimal estimated = estimateValue(orderItem.getPriceAtOrderTime(), req.condition());

        TradeInRequest tradeIn = new TradeInRequest();
        tradeIn.setCustomer(customer);
        tradeIn.setOrder(order);
        tradeIn.setOrderItem(orderItem);
        tradeIn.setProductName(orderItem.getProduct().getName());
        tradeIn.setCondition(req.condition());
        tradeIn.setEstimatedValue(estimated);

        TradeInRequest saved = tradeInRepository.save(tradeIn);

        sendReturnLabelEmail(customer, saved);

        return toResponse(saved);
    }

    @Transactional
    public TradeInResponse approveTradeIn(Long tradeInId) {
        TradeInRequest tradeIn = loadById(tradeInId);

        if (tradeIn.getStatus() != TradeInStatus.PENDING) {
            throw new IllegalStateException("Nur offene Trade-In-Anfragen können genehmigt werden.");
        }

        String couponCode = generateCouponCode();
        BigDecimal discountPercent = tradeIn.getEstimatedValue()
                .divide(tradeIn.getOrderItem().getPriceAtOrderTime(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        Coupon coupon = new Coupon();
        coupon.setCustomer(tradeIn.getCustomer());
        coupon.setCode(couponCode);
        coupon.setDiscountPercent(discountPercent);
        coupon.setValidUntil(LocalDate.now().plusMonths(12));
        couponRepository.save(coupon);

        tradeIn.setStatus(TradeInStatus.COMPLETED);
        tradeIn.setCouponCode(couponCode);
        tradeIn.setUpdatedAt(Instant.now());
        tradeInRepository.save(tradeIn);

        sendCouponEmail(tradeIn.getCustomer(), tradeIn, couponCode);

        return toResponse(tradeIn);
    }

    @Transactional
    public TradeInResponse rejectTradeIn(Long tradeInId) {
        TradeInRequest tradeIn = loadById(tradeInId);

        if (tradeIn.getStatus() != TradeInStatus.PENDING) {
            throw new IllegalStateException("Nur offene Trade-In-Anfragen können abgelehnt werden.");
        }

        tradeIn.setStatus(TradeInStatus.REJECTED);
        tradeIn.setUpdatedAt(Instant.now());
        tradeInRepository.save(tradeIn);

        emailService.sendEmailToCustomer(
                tradeIn.getCustomer(),
                "Deine Trade-In-Anfrage wurde abgelehnt",
                "Hallo " + tradeIn.getCustomer().getUsername() + ",\n\n"
                + "leider konnten wir deine Trade-In-Anfrage für \"" + tradeIn.getProductName()
                + "\" nicht genehmigen.\n\n"
                + "Bitte wende dich bei Fragen an unseren Kundendienst.\n\n"
                + "Viele Grüße\nDein Webshop-Team");

        return toResponse(tradeIn);
    }

    public List<TradeInResponse> listForCustomer(Long customerId) {
        return tradeInRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<TradeInResponse> listAll() {
        return tradeInRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderItem findOrderItemForCustomer(Long orderItemId, Long customerId) {
        return orderItemRepository.findByIdAndOrderCustomerId(orderItemId, customerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order item not found or does not belong to customer: " + orderItemId));
    }

    private TradeInRequest loadById(Long id) {
        return tradeInRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Trade-In-Anfrage nicht gefunden: " + id));
    }

    private String generateCouponCode() {
        String code;
        do {
            code = "TRADEIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (couponRepository.findByCode(code).isPresent());
        return code;
    }

    private void sendReturnLabelEmail(User customer, TradeInRequest tradeIn) {
        emailService.sendEmailToCustomer(
                customer,
                "Dein kostenloses Rücksendeetikett – Trade-In #" + tradeIn.getId(),
                "Hallo " + customer.getUsername() + ",\n\n"
                + "vielen Dank für deine Trade-In-Anfrage für \"" + tradeIn.getProductName() + "\"!\n\n"
                + "Geschätzter Gutschriftswert: " + tradeIn.getEstimatedValue() + " €\n\n"
                + "Bitte drucke das beigefügte kostenlose Rücksendeetikett aus und sende den Artikel "
                + "innerhalb von 14 Tagen an uns zurück. Nach Eingang und Prüfung "
                + "erhältst du automatisch deinen Shop-Gutschein.\n\n"
                + "[RÜCKSENDEETIKETT: TRACKING-NR TRADEIN-" + tradeIn.getId() + "]\n\n"
                + "Viele Grüße\nDein Webshop-Team");
    }

    private void sendCouponEmail(User customer, TradeInRequest tradeIn, String couponCode) {
        emailService.sendEmailToCustomer(
                customer,
                "Dein Trade-In-Gutschein ist bereit! – " + couponCode,
                "Hallo " + customer.getUsername() + ",\n\n"
                + "dein eingesendeter Artikel \"" + tradeIn.getProductName()
                + "\" wurde erfolgreich geprüft.\n\n"
                + "Hier ist dein Gutscheincode: " + couponCode + "\n"
                + "Gutschriftwert: " + tradeIn.getEstimatedValue() + " €\n"
                + "Gültig bis: 12 Monate ab heute\n\n"
                + "Du kannst den Code bei deiner nächsten Bestellung einlösen.\n\n"
                + "Vielen Dank, dass du beim Recycling mitmachst!\n\n"
                + "Viele Grüße\nDein Webshop-Team");
    }

    private TradeInResponse toResponse(TradeInRequest t) {
        return new TradeInResponse(
                t.getId(),
                t.getCustomer().getId(),
                t.getCustomer().getUsername(),
                t.getCustomer().getEmail(),
                t.getOrder().getId(),
                t.getOrderItem().getId(),
                t.getProductName(),
                t.getCondition(),
                t.getStatus(),
                t.getEstimatedValue(),
                t.getCouponCode(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
