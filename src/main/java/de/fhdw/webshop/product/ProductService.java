package de.fhdw.webshop.product;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.product.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    public List<ProductResponse> listProducts(Boolean purchasableOnly, String category, String searchTerm) {
        return listProducts(purchasableOnly, category, searchTerm, null);
    }

    public List<ProductResponse> listProducts(
            Boolean purchasableOnly,
            String category,
            String searchTerm,
            List<ProductEcoScore> ecoScores) {
        // Normalize null Strings to "" — JPQL IS NULL on String params infers bytea in PostgreSQL,
        // breaking LOWER(). The repository query uses = '' as the "no filter" sentinel instead.
        String normalizedCategory = (category == null) ? "" : category;
        String normalizedSearchTerm = (searchTerm == null) ? "" : searchTerm;
        boolean filterByEcoScore = ecoScores != null && !ecoScores.isEmpty();
        List<ProductEcoScore> normalizedEcoScores = filterByEcoScore
                ? ecoScores
                : Arrays.asList(ProductEcoScore.values());
        return productRepository.searchProducts(
                        purchasableOnly,
                        normalizedCategory,
                        filterByEcoScore,
                        normalizedEcoScores,
                        normalizedSearchTerm)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductResponse getProduct(Long productId) {
        return toResponse(loadProduct(productId));
    }

    /** US #31 — Returns the price after applying the customer's best active discount. */
    public ProductPriceResponse getPriceForCustomer(Long productId, User customer,
                                                    DiscountLookupPort discountLookupPort) {
        Product product = loadProduct(productId);
        BigDecimal bestDiscountPercent = discountLookupPort.findBestActiveDiscountPercent(customer.getId(), productId);
        BigDecimal effectivePrice = applyDiscount(product.getRecommendedRetailPrice(), bestDiscountPercent);
        return new ProductPriceResponse(productId, product.getRecommendedRetailPrice(), effectivePrice, bestDiscountPercent);
    }

    /** US #13 — Add a new article to the catalogue. */
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest, User actingUser) {
        Product product = new Product();
        product.setName(productRequest.name());
        product.setDescription(productRequest.description());
        product.setImageUrl(productRequest.imageUrl());
        product.setRecommendedRetailPrice(productRequest.recommendedRetailPrice());
        product.setCo2EmissionKg(productRequest.co2EmissionKg());
        product.setEcoScore(productRequest.ecoScore() == null ? ProductEcoScore.NONE : productRequest.ecoScore());
        product.setCategory(productRequest.category());
        product.setStock(25);
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "CREATE_PRODUCT", savedProduct,
                "Product created: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #14 — Remove an article from the catalogue. */
    @Transactional
    public void deleteProduct(Long productId, User actingUser) {
        Product product = loadProduct(productId);
        productRepository.delete(product);
        recordProductAction(actingUser, "DELETE_PRODUCT", product,
                "Product deleted: " + product.getName());
    }

    /** US #15 — Toggle whether customers can see and buy the article. */
    @Transactional
    public ProductResponse setPurchasable(Long productId, boolean purchasable, User actingUser) {
        Product product = loadProduct(productId);
        product.setPurchasable(purchasable);
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "SET_PRODUCT_PURCHASABLE", savedProduct,
                "Product purchasable set to " + purchasable + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #26 — Toggle promoted flag (highlighted on storefront). */
    @Transactional
    public ProductResponse setPromoted(Long productId, boolean promoted, User actingUser) {
        Product product = loadProduct(productId);
        product.setPromoted(promoted);
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "SET_PRODUCT_PROMOTED", savedProduct,
                "Product promoted set to " + promoted + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #16 — Update description text. */
    @Transactional
    public ProductResponse updateDescription(Long productId, UpdateDescriptionRequest updateDescriptionRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setDescription(updateDescriptionRequest.description());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_DESCRIPTION", savedProduct,
                "Product description updated: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #17 — Update product image URL. */
    @Transactional
    public ProductResponse updateImage(Long productId, UpdateImageRequest updateImageRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setImageUrl(updateImageRequest.imageUrl());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_IMAGE", savedProduct,
                "Product image updated: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #18 — Update recommended retail price (SALES_EMPLOYEE only). */
    @Transactional
    public ProductResponse updatePrice(Long productId, UpdatePriceRequest updatePriceRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setRecommendedRetailPrice(updatePriceRequest.recommendedRetailPrice());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_PRICE", savedProduct,
                "Product price updated to " + updatePriceRequest.recommendedRetailPrice() + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    /** US #196 — Update the estimated product CO2 footprint. */
    @Transactional
    public ProductResponse updateCo2Emission(Long productId, UpdateCo2EmissionRequest updateCo2EmissionRequest) {
        Product product = loadProduct(productId);
        product.setCo2EmissionKg(updateCo2EmissionRequest.co2EmissionKg());
        return toResponse(productRepository.save(product));
    }

    /** US #198 - Update the product Eco-Score. */
    @Transactional
    public ProductResponse updateEcoScore(Long productId, UpdateEcoScoreRequest updateEcoScoreRequest) {
        Product product = loadProduct(productId);
        product.setEcoScore(updateEcoScoreRequest.ecoScore());
        return toResponse(productRepository.save(product));
    }

    public Product loadProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
    }

    private BigDecimal applyDiscount(BigDecimal price, BigDecimal discountPercent) {
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getRecommendedRetailPrice(),
                product.getCo2EmissionKg(),
                product.getEcoScore(),
                product.getCategory(),
                product.getStock(),
                product.isPurchasable(),
                product.isPromoted(),
                product.getCreatedAt()
        );
    }

    private void recordProductAction(User actingUser, String action, Product product, String details) {
        auditLogService.record(
                actingUser,
                action,
                "Product",
                product.getId(),
                AuditInitiator.ADMIN,
                details);
    }

    /** Port interface so ProductService does not depend on the discount package directly. */
    @FunctionalInterface
    public interface DiscountLookupPort {
        BigDecimal findBestActiveDiscountPercent(Long customerId, Long productId);
    }
}
