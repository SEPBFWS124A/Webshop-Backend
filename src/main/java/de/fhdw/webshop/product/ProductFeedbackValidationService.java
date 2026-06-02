package de.fhdw.webshop.product;

import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductFeedbackValidationService {

    private static final String INTERNAL_SELLER = "webshop";

    // Ein Kauf gilt ab Bestellbestaetigung als bewertbar – ausgenommen sind
    // noch nicht bestaetigte oder abgebrochene Bestellungen.
    private static final Set<OrderStatus> NON_REVIEWABLE_STATUSES = Set.of(
            OrderStatus.PENDING,
            OrderStatus.Pending_Approval,
            OrderStatus.Rejected,
            OrderStatus.CANCELLED
    );

    private final ProductRepository productRepository;
    private final ProductFeedbackRepository productFeedbackRepository;
    private final OrderRepository orderRepository;

    public Product assertExternalSeller(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produkt nicht gefunden: " + productId));
        if (INTERNAL_SELLER.equalsIgnoreCase(product.getSellerName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Produkte des eigenen Webshops können nicht bewertet werden.");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public void assertVerifiedPurchase(Long productId, Long userId) {
        boolean hasPurchased = orderRepository.existsReviewableOrderWithProduct(
                productId, userId, NON_REVIEWABLE_STATUSES);
        if (!hasPurchased) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Dieses Produkt kann erst nach einem abgeschlossenen Kauf bewertet werden.");
        }
    }

    @Transactional(readOnly = true)
    public boolean hasVerifiedPurchase(Long productId, Long userId) {
        return orderRepository.existsReviewableOrderWithProduct(
                productId, userId, NON_REVIEWABLE_STATUSES);
    }

    @Transactional(readOnly = true)
    public ProductFeedbackStatsResponse getStats(Long productId) {
        Double avg = productFeedbackRepository.findAverageRatingByProductId(productId);
        long count = productFeedbackRepository.countByProductId(productId);
        return new ProductFeedbackStatsResponse(avg != null ? avg : 0.0, count);
    }

    @Transactional(readOnly = true)
    public double getAverageRatingBySeller(String sellerName) {
        Double avg = productFeedbackRepository.findAverageRatingBySellerName(sellerName);
        return avg != null ? avg : 0.0;
    }
}
