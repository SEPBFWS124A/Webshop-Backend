package de.fhdw.webshop.marketplacedispute;

import de.fhdw.webshop.marketplacedispute.dto.CreateMarketplaceDisputeRequest;
import de.fhdw.webshop.marketplacedispute.dto.MarketplaceDisputeHistoryResponse;
import de.fhdw.webshop.marketplacedispute.dto.MarketplaceDisputeResponse;
import de.fhdw.webshop.marketplacedispute.dto.MarketplaceDisputeStatusUpdateRequest;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.sellerportal.SellerProfileRepository;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceDisputeService {

    private static final String WEBSHOP_SELLER_NAME = "Webshop";
    private static final Set<MarketplaceDisputeStatus> ACTIVE_STATUSES =
            Set.of(MarketplaceDisputeStatus.OPEN, MarketplaceDisputeStatus.UNDER_REVIEW);

    private final MarketplaceDisputeRepository disputeRepository;
    private final OrderRepository orderRepository;
    private final SellerProfileRepository sellerProfileRepository;

    @Transactional
    public MarketplaceDisputeResponse createDispute(
            User customer,
            Long orderId,
            Long orderItemId,
            CreateMarketplaceDisputeRequest request
    ) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> new EntityNotFoundException("Bestellung nicht gefunden: " + orderId));
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Konfliktfälle können erst für gelieferte Marketplace-Bestellungen eröffnet werden.");
        }

        OrderItem orderItem = order.getItems().stream()
                .filter(item -> item.getId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Bestellposition nicht gefunden: " + orderItemId));
        String sellerName = resolveSellerName(orderItem);
        if (!isMarketplaceSeller(sellerName)) {
            throw new IllegalArgumentException("Konfliktfälle sind nur für Drittanbieter-Artikel möglich.");
        }
        if (disputeRepository.existsByOrderItemIdAndStatusIn(orderItemId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("Für diese Bestellposition existiert bereits ein offener Konfliktfall.");
        }

        MarketplaceDispute dispute = new MarketplaceDispute();
        dispute.setOrder(order);
        dispute.setOrderItem(orderItem);
        dispute.setCustomer(customer);
        dispute.setSellerName(sellerName);
        dispute.setReason(request.reason());
        dispute.setStatus(MarketplaceDisputeStatus.OPEN);
        dispute.setDescription(blankToNull(request.description()));
        dispute.setPayoutBlocked(true);

        for (String imageUrl : normalizeImageUrls(request.imageUrls())) {
            MarketplaceDisputeImage image = new MarketplaceDisputeImage();
            image.setDispute(dispute);
            image.setImageUrl(imageUrl);
            dispute.getImages().add(image);
        }

        addHistory(dispute, MarketplaceDisputeStatus.OPEN, customer, "Konfliktfall durch Kunden eröffnet.");
        return toResponse(disputeRepository.save(dispute));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceDisputeResponse> listCustomerDisputes(User customer) {
        return disputeRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceDisputeResponse> listCustomerOrderDisputes(User customer, Long orderId) {
        return disputeRepository.findByOrderIdAndCustomerIdOrderByCreatedAtDesc(orderId, customer.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceDisputeResponse> listSellerDisputes(User sellerUser) {
        String sellerName = requireSellerDisplayName(sellerUser);
        return disputeRepository.findBySellerNameIgnoreCaseOrderByCreatedAtDesc(sellerName).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceDisputeResponse getSellerDispute(User sellerUser, Long disputeId) {
        String sellerName = requireSellerDisplayName(sellerUser);
        return disputeRepository.findByIdAndSellerNameIgnoreCase(disputeId, sellerName)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Konfliktfall nicht gefunden: " + disputeId));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceDisputeResponse> listAdminDisputes(MarketplaceDisputeStatus status) {
        return disputeRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(dispute -> status == null || dispute.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MarketplaceDisputeResponse updateStatus(
            User employee,
            Long disputeId,
            MarketplaceDisputeStatusUpdateRequest request
    ) {
        MarketplaceDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new EntityNotFoundException("Konfliktfall nicht gefunden: " + disputeId));
        MarketplaceDisputeStatus status = request.status();
        dispute.setStatus(status);
        dispute.setPayoutBlocked(ACTIVE_STATUSES.contains(status));
        if (!ACTIVE_STATUSES.contains(status)) {
            dispute.setResolvedAt(Instant.now());
        } else {
            dispute.setResolvedAt(null);
        }
        addHistory(dispute, status, employee, blankToNull(request.note()));
        return toResponse(disputeRepository.save(dispute));
    }

    public boolean isMarketplaceSeller(String sellerName) {
        return sellerName != null && !sellerName.isBlank() && !WEBSHOP_SELLER_NAME.equalsIgnoreCase(sellerName.trim());
    }

    private String resolveSellerName(OrderItem orderItem) {
        String sellerName = blankToNull(orderItem.getSellerName());
        if (sellerName != null) {
            return sellerName;
        }
        if (orderItem.getProduct() != null) {
            return blankToNull(orderItem.getProduct().getSellerName());
        }
        return WEBSHOP_SELLER_NAME;
    }

    private String requireSellerDisplayName(User sellerUser) {
        return sellerProfileRepository.findByUserId(sellerUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Kein aktives Verkaeuferprofil gefunden."))
                .getDisplayName();
    }

    private void addHistory(MarketplaceDispute dispute, MarketplaceDisputeStatus status, User user, String note) {
        MarketplaceDisputeHistory history = new MarketplaceDisputeHistory();
        history.setDispute(dispute);
        history.setStatus(status);
        history.setChangedBy(user);
        history.setChangedByUsername(user == null ? "System" : user.getUsername());
        history.setNote(note);
        dispute.getHistory().add(history);
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        return imageUrls.stream()
                .map(this::blankToNull)
                .filter(value -> value != null)
                .distinct()
                .limit(3)
                .toList();
    }

    private MarketplaceDisputeResponse toResponse(MarketplaceDispute dispute) {
        OrderItem item = dispute.getOrderItem();
        return new MarketplaceDisputeResponse(
                dispute.getId(),
                dispute.getOrder().getId(),
                dispute.getOrder().getOrderNumber(),
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                dispute.getOrder().getCustomerName() == null ? dispute.getCustomer().getUsername() : dispute.getOrder().getCustomerName(),
                dispute.getSellerName(),
                dispute.getReason(),
                dispute.getStatus(),
                dispute.getDescription(),
                dispute.isPayoutBlocked(),
                dispute.getCreatedAt(),
                dispute.getUpdatedAt(),
                dispute.getResolvedAt(),
                dispute.getImages().stream()
                        .sorted(Comparator.comparing(MarketplaceDisputeImage::getId))
                        .map(MarketplaceDisputeImage::getImageUrl)
                        .toList(),
                dispute.getHistory().stream()
                        .sorted(Comparator.comparing(MarketplaceDisputeHistory::getChangedAt))
                        .map(history -> new MarketplaceDisputeHistoryResponse(
                                history.getId(),
                                history.getStatus(),
                                history.getChangedByUsername(),
                                history.getChangedAt(),
                                history.getNote()))
                        .toList()
        );
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
