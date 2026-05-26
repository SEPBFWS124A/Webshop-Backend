package de.fhdw.webshop.productbundle;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.product.ProductType;
import de.fhdw.webshop.productbundle.dto.ProductBundleItemRequest;
import de.fhdw.webshop.productbundle.dto.ProductBundleItemResponse;
import de.fhdw.webshop.productbundle.dto.ProductBundleRequest;
import de.fhdw.webshop.productbundle.dto.ProductBundleResponse;
import de.fhdw.webshop.reservation.StockReservationService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductBundleService {

    private final ProductBundleRepository productBundleRepository;
    private final ProductService productService;
    private final StockReservationService stockReservationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProductBundleResponse> listActiveBundles(Long productId) {
        List<ProductBundle> bundles = productId == null
                ? productBundleRepository.findByActiveTrueOrderByFeaturedDescCreatedAtDescIdDesc()
                : productBundleRepository.findDistinctByActiveTrueAndItemsProductIdOrderByFeaturedDescCreatedAtDescIdDesc(productId);
        return bundles.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductBundleResponse> listAllBundles() {
        return productBundleRepository.findAll().stream()
                .sorted((left, right) -> {
                    int featuredSort = Boolean.compare(right.isFeatured(), left.isFeatured());
                    if (featuredSort != 0) {
                        return featuredSort;
                    }
                    int activeSort = Boolean.compare(right.isActive(), left.isActive());
                    if (activeSort != 0) {
                        return activeSort;
                    }
                    return right.getCreatedAt().compareTo(left.getCreatedAt());
                })
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductBundleResponse getBundle(Long bundleId) {
        return toResponse(loadBundle(bundleId));
    }

    @Transactional
    public ProductBundleResponse createBundle(ProductBundleRequest request, User actingUser) {
        ProductBundle bundle = new ProductBundle();
        applyRequest(bundle, request);
        ProductBundle savedBundle = productBundleRepository.save(bundle);
        recordAction(actingUser, "CREATE_PRODUCT_BUNDLE", savedBundle, "Produktbundle angelegt: " + savedBundle.getTitle());
        return toResponse(savedBundle);
    }

    @Transactional
    public ProductBundleResponse updateBundle(Long bundleId, ProductBundleRequest request, User actingUser) {
        ProductBundle bundle = loadBundle(bundleId);
        applyRequest(bundle, request);
        ProductBundle savedBundle = productBundleRepository.save(bundle);
        recordAction(actingUser, "UPDATE_PRODUCT_BUNDLE", savedBundle, "Produktbundle aktualisiert: " + savedBundle.getTitle());
        return toResponse(savedBundle);
    }

    @Transactional
    public void deleteBundle(Long bundleId, User actingUser) {
        ProductBundle bundle = loadBundle(bundleId);
        productBundleRepository.delete(bundle);
        recordAction(actingUser, "DELETE_PRODUCT_BUNDLE", bundle, "Produktbundle gelöscht: " + bundle.getTitle());
    }

    @Transactional(readOnly = true)
    public ProductBundle loadBundleForPurchase(Long bundleId) {
        ProductBundle bundle = loadBundle(bundleId);
        if (!bundle.isActive()) {
            throw new IllegalArgumentException("Dieses Produktbundle ist aktuell nicht aktiv.");
        }
        if (bundle.getItems().isEmpty()) {
            throw new IllegalArgumentException("Dieses Produktbundle enthält keine kaufbaren Artikel.");
        }
        for (ProductBundleItem item : bundle.getItems()) {
            Product product = item.getProduct();
            if (!product.isPurchasable()) {
                throw new IllegalArgumentException("Das Bundle enthält nicht bestellbare Artikel.");
            }
            if (product.getProductType() != ProductType.DIGITAL_GIFT_CARD
                    && stockReservationService.getAvailableQuantity(product) < item.getQuantity()) {
                throw new IllegalArgumentException("Mindestens ein Bundle-Artikel ist nicht ausreichend verfügbar.");
            }
        }
        return bundle;
    }

    public BigDecimal calculateDiscountedUnitPrice(Product product, BigDecimal bundleDiscountPercent) {
        BigDecimal percent = bundleDiscountPercent == null ? BigDecimal.ZERO : bundleDiscountPercent;
        BigDecimal multiplier = BigDecimal.ONE.subtract(percent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return product.getRecommendedRetailPrice()
                .multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public ProductBundle loadBundle(Long bundleId) {
        return productBundleRepository.findById(bundleId)
                .orElseThrow(() -> new EntityNotFoundException("Product bundle not found: " + bundleId));
    }

    private void applyRequest(ProductBundle bundle, ProductBundleRequest request) {
        List<ProductBundleItemRequest> itemRequests = request.items() == null ? List.of() : request.items();
        if (itemRequests.isEmpty()) {
            throw new IllegalArgumentException("Ein Produktbundle benötigt mindestens einen Artikel.");
        }

        bundle.setTitle(request.title().trim());
        bundle.setDescription(trimToNull(request.description()));
        bundle.setImageUrl(trimToNull(request.imageUrl()));
        bundle.setDiscountPercent(request.discountPercent().setScale(2, RoundingMode.HALF_UP));
        bundle.setActive(Boolean.TRUE.equals(request.active()));
        bundle.setFeatured(Boolean.TRUE.equals(request.featured()));

        Set<Long> seenProductIds = new LinkedHashSet<>();
        List<ProductBundleItem> normalizedItems = new ArrayList<>();
        for (int index = 0; index < itemRequests.size(); index++) {
            ProductBundleItemRequest itemRequest = itemRequests.get(index);
            Long productId = itemRequest.productId();
            if (!seenProductIds.add(productId)) {
                throw new IllegalArgumentException("Jeder Artikel darf in einem Bundle nur einmal vorkommen.");
            }

            Product product = productService.loadProduct(productId);
            ProductBundleItem bundleItem = new ProductBundleItem();
            bundleItem.setBundle(bundle);
            bundleItem.setProduct(product);
            bundleItem.setQuantity(itemRequest.quantity());
            bundleItem.setDisplayOrder(index);
            normalizedItems.add(bundleItem);
        }

        bundle.getItems().clear();
        bundle.getItems().addAll(normalizedItems);
    }

    private ProductBundleResponse toResponse(ProductBundle bundle) {
        List<ProductBundleItemResponse> itemResponses = bundle.getItems().stream()
                .map(item -> toItemResponse(item, bundle.getDiscountPercent()))
                .toList();
        BigDecimal originalTotalPrice = itemResponses.stream()
                .map(ProductBundleItemResponse::lineOriginalTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal bundleTotalPrice = itemResponses.stream()
                .map(ProductBundleItemResponse::lineBundleTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        boolean available = itemResponses.stream().allMatch(item ->
                item.purchasable()
                        && (item.availableStock() == null
                        || item.availableStock() >= item.quantity()));
        int totalItemCount = itemResponses.stream().mapToInt(ProductBundleItemResponse::quantity).sum();

        return new ProductBundleResponse(
                bundle.getId(),
                bundle.getTitle(),
                bundle.getDescription(),
                bundle.getImageUrl(),
                bundle.getDiscountPercent(),
                bundle.isActive(),
                bundle.isFeatured(),
                available,
                totalItemCount,
                originalTotalPrice,
                bundleTotalPrice,
                originalTotalPrice.subtract(bundleTotalPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                bundle.getCreatedAt(),
                bundle.getUpdatedAt(),
                itemResponses
        );
    }

    private ProductBundleItemResponse toItemResponse(ProductBundleItem item, BigDecimal discountPercent) {
        Product product = item.getProduct();
        BigDecimal originalUnitPrice = product.getRecommendedRetailPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountedUnitPrice = calculateDiscountedUnitPrice(product, discountPercent);
        int availableStock = product.getProductType() == ProductType.DIGITAL_GIFT_CARD
                ? 999999
                : stockReservationService.getAvailableQuantity(product);
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
        return new ProductBundleItemResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getCategory(),
                product.getSellerName(),
                product.getSku(),
                product.getEcoScore(),
                product.getCo2EmissionKg(),
                item.getQuantity(),
                product.getStock(),
                availableStock,
                product.isPurchasable(),
                originalUnitPrice,
                discountedUnitPrice,
                originalUnitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP),
                discountedUnitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private void recordAction(User actingUser, String action, ProductBundle bundle, String details) {
        auditLogService.record(actingUser, action, "ProductBundle", bundle.getId(), AuditInitiator.USER, details);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
