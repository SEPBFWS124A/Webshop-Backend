package de.fhdw.webshop.admin.marketplace;

import de.fhdw.webshop.admin.marketplace.dto.AdminProductReviewDto;
import de.fhdw.webshop.admin.marketplace.dto.AdminSellerReviewDto;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductFeedback;
import de.fhdw.webshop.product.ProductFeedbackRepository;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.sellerreview.SellerReview;
import de.fhdw.webshop.sellerreview.SellerReviewRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/marketplace")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
public class AdminMarketplaceReviewController {

    private final ProductFeedbackRepository productFeedbackRepository;
    private final SellerReviewRepository sellerReviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ── Product Reviews ───────────────────────────────────────────────────────

    @GetMapping("/product-reviews")
    public Page<AdminProductReviewDto> listProductReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean approved) {

        Pageable pageable = PageRequest.of(page, size);
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<ProductFeedback> resultPage;
        if (approved == null && searchParam == null) {
            resultPage = productFeedbackRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else if (approved != null && searchParam == null) {
            resultPage = productFeedbackRepository.findByApprovedOrderByCreatedAtDesc(approved, pageable);
        } else if (approved == null) {
            resultPage = productFeedbackRepository.findBySearchTermOrderByCreatedAtDesc(searchParam, pageable);
        } else {
            resultPage = productFeedbackRepository.findByApprovedAndSearchTerm(approved, searchParam, pageable);
        }
        return resultPage.map(this::toProductReviewDto);
    }

    @PutMapping("/product-reviews/{id}/approve")
    public AdminProductReviewDto approveProductReview(@PathVariable long id) {
        ProductFeedback feedback = productFeedbackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Produktbewertung nicht gefunden: " + id));
        feedback.setApproved(true);
        return toProductReviewDto(productFeedbackRepository.save(feedback));
    }

    @DeleteMapping("/product-reviews/{id}")
    public ResponseEntity<Void> deleteProductReview(@PathVariable long id) {
        if (!productFeedbackRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        productFeedbackRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Seller Reviews ────────────────────────────────────────────────────────

    @GetMapping("/seller-reviews")
    public Page<AdminSellerReviewDto> listSellerReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean approved) {

        Pageable pageable = PageRequest.of(page, size);
        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;
        Page<SellerReview> resultPage;
        if (approved == null && searchParam == null) {
            resultPage = sellerReviewRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else if (approved != null && searchParam == null) {
            resultPage = sellerReviewRepository.findByApprovedOrderByCreatedAtDesc(approved, pageable);
        } else if (approved == null) {
            resultPage = sellerReviewRepository.findBySearchTermOrderByCreatedAtDesc(searchParam, pageable);
        } else {
            resultPage = sellerReviewRepository.findByApprovedAndSearchTerm(approved, searchParam, pageable);
        }
        return resultPage.map(this::toSellerReviewDto);
    }

    @PutMapping("/seller-reviews/{id}/approve")
    public AdminSellerReviewDto approveSellerReview(@PathVariable long id) {
        SellerReview review = sellerReviewRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Händlerbewertung nicht gefunden: " + id));
        review.setApproved(true);
        return toSellerReviewDto(sellerReviewRepository.save(review));
    }

    @DeleteMapping("/seller-reviews/{id}")
    public ResponseEntity<Void> deleteSellerReview(@PathVariable long id) {
        if (!sellerReviewRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        sellerReviewRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AdminProductReviewDto toProductReviewDto(ProductFeedback f) {
        String productTitle = Optional.ofNullable(f.getProductId())
                .flatMap(productRepository::findById)
                .map(Product::getName)
                .orElse("Unbekanntes Produkt");
        String userName = Optional.ofNullable(f.getUserId())
                .flatMap(userRepository::findById)
                .map(User::getUsername)
                .orElse("Anonym");
        return new AdminProductReviewDto(
                f.getId(),
                f.getProductId(),
                productTitle,
                f.getUserId(),
                userName,
                f.getRating(),
                f.getComment(),
                f.getCreatedAt(),
                f.getSource(),
                f.getApproved()
        );
    }

    private AdminSellerReviewDto toSellerReviewDto(SellerReview r) {
        User customer = r.getCustomer();
        return new AdminSellerReviewDto(
                r.getId(),
                r.getSellerName(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getUsername() : "Anonym",
                r.getOrder().getOrderNumber(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt(),
                r.getApproved()
        );
    }
}
