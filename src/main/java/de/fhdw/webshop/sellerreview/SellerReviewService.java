package de.fhdw.webshop.sellerreview;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.helpfulvote.HelpfulVoteService;
import de.fhdw.webshop.helpfulvote.HelpfulVoteTargetType;
import de.fhdw.webshop.helpfulvote.dto.HelpfulVoteSummary;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.sellerreview.dto.CreateSellerReviewRequest;
import de.fhdw.webshop.sellerreview.dto.SellerReviewImageResponse;
import de.fhdw.webshop.sellerreview.dto.SellerReviewResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SellerReviewService {

    private static final String DEFAULT_SELLER_NAME = "Webshop";
    private static final int MAX_IMAGES_PER_REVIEW = 3;
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    // Eine Bestellung ist ab Bestellbestaetigung bewertbar – also alle Status
    // ausser noch nicht bestaetigten oder abgebrochenen Bestellungen.
    private static final Set<OrderStatus> NON_REVIEWABLE_STATUSES = Set.of(
            OrderStatus.PENDING,
            OrderStatus.Pending_Approval,
            OrderStatus.Rejected,
            OrderStatus.CANCELLED
    );

    private final SellerReviewRepository sellerReviewRepository;
    private final SellerReviewImageRepository sellerReviewImageRepository;
    private final OrderRepository orderRepository;
    private final AuditLogService auditLogService;
    private final HelpfulVoteService helpfulVoteService;

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listReviewsForSeller(String sellerName) {
        return sellerReviewRepository.findBySellerNameIgnoreCaseAndApprovedTrueOrderByCreatedAtDesc(sellerName)
                .stream()
                .map(review -> toResponse(review, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listMyReviews(User currentUser) {
        return sellerReviewRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(review -> toResponse(review, currentUser))
                .sorted((left, right) -> {
                    int scoreCompare = Long.compare(right.helpfulScore(), left.helpfulScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return right.createdAt().compareTo(left.createdAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listMyReviewsForOrder(Long orderId, User currentUser) {
        return sellerReviewRepository.findByOrderIdAndCustomerIdOrderByCreatedAtDesc(orderId, currentUser.getId())
                .stream()
                .map(review -> toResponse(review, currentUser))
                .sorted((left, right) -> {
                    int scoreCompare = Long.compare(right.helpfulScore(), left.helpfulScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return right.createdAt().compareTo(left.createdAt());
                })
                .toList();
    }

    @Transactional
    public SellerReviewResponse createReview(Long orderId, User currentUser, CreateSellerReviewRequest request) {
        return createReview(orderId, currentUser, request, List.of());
    }

    @Transactional
    public SellerReviewResponse createReview(Long orderId, User currentUser, CreateSellerReviewRequest request, List<MultipartFile> images) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        if (NON_REVIEWABLE_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("Verkaeufer können erst nach Bestellbestaetigung bewertet werden.");
        }

        String requestedSeller = normalizeRequired(request.sellerName(), "Bitte einen Verkaeufer aus der Bestellung auswaehlen.");
        validateRating(request.rating());
        validateReviewImages(images);
        Map<String, String> purchasedSellers = resolvePurchasedSellers(order);
        String canonicalSeller = purchasedSellers.get(requestedSeller.toLowerCase());
        if (canonicalSeller == null) {
            throw new IllegalArgumentException("Dieser Verkaeufer wurde in der Bestellung nicht gekauft.");
        }

        if (sellerReviewRepository.existsByOrderIdAndCustomerIdAndSellerNameIgnoreCase(orderId, currentUser.getId(), canonicalSeller)) {
            throw new IllegalArgumentException("Für diesen Verkaeufer wurde zu dieser Bestellung bereits eine Bewertung abgegeben.");
        }

        SellerReview review = new SellerReview();
        review.setOrder(order);
        review.setCustomer(currentUser);
        review.setSellerName(canonicalSeller);
        review.setRating(request.rating());
        review.setComment(normalizeNullable(request.comment()));

        SellerReview savedReview = sellerReviewRepository.save(review);
        saveReviewImages(savedReview, images);
        auditLogService.record(currentUser, "CREATE_SELLER_REVIEW", "SellerReview", savedReview.getId(),
                AuditInitiator.USER,
                "Seller review created for " + canonicalSeller + " in order " + order.getOrderNumber());

        return toResponse(savedReview, currentUser);
    }

    @Transactional
    public SellerReviewResponse voteReview(Long reviewId, User currentUser, boolean helpful) {
        SellerReview review = sellerReviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));
        helpfulVoteService.toggleVote(HelpfulVoteTargetType.SELLER_REVIEW, reviewId, currentUser, helpful);
        return toResponse(review, currentUser);
    }

    @Transactional(readOnly = true)
    public List<SellerReviewImageResponse> listImagesForModeration() {
        return sellerReviewImageRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toImageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getReviewableOrderIds(String sellerName, User currentUser) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .filter(order -> !NON_REVIEWABLE_STATUSES.contains(order.getStatus()))
                .filter(order -> order.getItems().stream()
                        .anyMatch(item -> sellerName.equalsIgnoreCase(normalizeSellerName(item))))
                .filter(order -> !sellerReviewRepository.existsByOrderIdAndCustomerIdAndSellerNameIgnoreCase(
                        order.getId(), currentUser.getId(), sellerName))
                .map(order -> order.getId())
                .toList();
    }

    @Transactional
    public void deleteImage(Long imageId, User currentUser) {
        SellerReviewImage image = sellerReviewImageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Review image not found: " + imageId));
        Long reviewId = image.getReview().getId();
        sellerReviewImageRepository.delete(image);
        auditLogService.record(currentUser, "DELETE_SELLER_REVIEW_IMAGE", "SellerReviewImage", imageId,
                AuditInitiator.USER,
                "Review image removed from seller review " + reviewId);
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

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Bitte eine Sternebewertung zwischen 1 und 5 angeben.");
        }
    }

    private void validateReviewImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        if (images.size() > MAX_IMAGES_PER_REVIEW) {
            throw new IllegalArgumentException("Es können maximal 3 Bilder pro Rezension hochgeladen werden.");
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                throw new IllegalArgumentException("Leere Bilddateien können nicht hochgeladen werden.");
            }
            if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
                throw new IllegalArgumentException("Ein Rezensionsbild darf maximal 5 MB gross sein.");
            }
            String contentType = normalizeNullable(image.getContentType());
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new IllegalArgumentException("Bitte nur Bilder im Format JPG, PNG oder WebP hochladen.");
            }
        }
    }

    private void saveReviewImages(SellerReview review, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (MultipartFile upload : images) {
            SellerReviewImage image = new SellerReviewImage();
            image.setReview(review);
            image.setOriginalFilename(normalizeFilename(upload.getOriginalFilename()));
            image.setContentType(upload.getContentType().toLowerCase());
            image.setFileSizeBytes(upload.getSize());
            try {
                image.setImageData(upload.getBytes());
            } catch (IOException ex) {
                throw new IllegalArgumentException("Ein Rezensionsbild konnte nicht gelesen werden.");
            }
            sellerReviewImageRepository.save(image);
        }
    }

    private String normalizeFilename(String filename) {
        String normalized = normalizeNullable(filename);
        return normalized != null ? normalized : "rezensionsbild";
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private SellerReviewResponse toResponse(SellerReview review, User currentUser) {
        HelpfulVoteSummary votes = helpfulVoteService.summarize(
                HelpfulVoteTargetType.SELLER_REVIEW,
                review.getId(),
                currentUser
        );
        List<SellerReviewImageResponse> images = sellerReviewImageRepository
                .findByReviewIdOrderByCreatedAtAsc(review.getId())
                .stream()
                .map(this::toImageResponse)
                .toList();
        return new SellerReviewResponse(
                review.getId(),
                review.getOrder().getId(),
                review.getOrder().getOrderNumber(),
                review.getSellerName(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                votes.helpfulCount(),
                votes.notHelpfulCount(),
                votes.helpfulScore(),
                votes.currentUserVote(),
                images
        );
    }

    private SellerReviewImageResponse toImageResponse(SellerReviewImage image) {
        SellerReview review = image.getReview();
        User customer = review.getCustomer();
        String encoded = Base64.getEncoder().encodeToString(image.getImageData());
        String dataUrl = "data:" + image.getContentType() + ";base64," + encoded;
        return new SellerReviewImageResponse(
                image.getId(),
                review.getId(),
                review.getSellerName(),
                review.getOrder().getOrderNumber(),
                customer != null ? customer.getUsername() : null,
                customer != null ? customer.getEmail() : null,
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSizeBytes(),
                image.getCreatedAt(),
                dataUrl
        );
    }
}
