package de.fhdw.webshop.sellerreview;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.sellerreview.dto.CreateSellerReviewRequest;
import de.fhdw.webshop.sellerreview.dto.SellerReviewResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerReviewService {

    private static final String DEFAULT_SELLER_NAME = "Webshop";

    private final SellerReviewRepository sellerReviewRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listMyReviews(User currentUser) {
        return sellerReviewRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listMyReviewsForOrder(Long orderId, User currentUser) {
        return sellerReviewRepository.findByOrderIdAndCustomerIdOrderByCreatedAtDesc(orderId, currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SellerReviewResponse createReview(Long orderId, User currentUser, CreateSellerReviewRequest request) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Verkaeufer koennen erst nach einer abgeschlossenen Bestellung bewertet werden.");
        }

        String requestedSeller = normalizeRequired(request.sellerName(), "Bitte einen Verkaeufer aus der Bestellung auswaehlen.");
        Map<String, String> purchasedSellers = resolvePurchasedSellers(order);
        String canonicalSeller = purchasedSellers.get(requestedSeller.toLowerCase());
        if (canonicalSeller == null) {
            throw new IllegalArgumentException("Dieser Verkaeufer wurde in der Bestellung nicht gekauft.");
        }

        if (sellerReviewRepository.existsByOrderIdAndCustomerIdAndSellerNameIgnoreCase(orderId, currentUser.getId(), canonicalSeller)) {
            throw new IllegalArgumentException("Fuer diesen Verkaeufer wurde zu dieser Bestellung bereits eine Bewertung abgegeben.");
        }

        SellerReview review = new SellerReview();
        review.setOrder(order);
        review.setCustomer(currentUser);
        review.setSellerName(canonicalSeller);
        review.setRating(request.rating());
        review.setComment(normalizeNullable(request.comment()));

        SellerReview savedReview = sellerReviewRepository.save(review);
        auditLogService.record(currentUser, "CREATE_SELLER_REVIEW", "SellerReview", savedReview.getId(),
                AuditInitiator.USER,
                "Seller review created for " + canonicalSeller + " in order " + order.getOrderNumber());

        return toResponse(savedReview);
    }

    private Map<String, String> resolvePurchasedSellers(Order order) {
        Map<String, String> sellers = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            String sellerName = normalizeSellerName(item);
            sellers.putIfAbsent(sellerName.toLowerCase(), sellerName);
        }
        return sellers;
    }

    private String normalizeSellerName(OrderItem item) {
        String sellerName = normalizeNullable(item.getSellerName());
        if (sellerName != null) {
            return sellerName;
        }
        if (item.getProduct() != null) {
            sellerName = normalizeNullable(item.getProduct().getSellerName());
        }
        return sellerName != null ? sellerName : DEFAULT_SELLER_NAME;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private SellerReviewResponse toResponse(SellerReview review) {
        return new SellerReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getOrder().getOrderNumber(),
                review.getSellerName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
