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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts(Boolean purchasableOnly, String category, String searchTerm) {
        return listProducts(purchasableOnly, category, searchTerm, null);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts(
            Boolean purchasableOnly,
            String category,
            String searchTerm,
            List<ProductEcoScore> ecoScores) {
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

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long productId) {
        return toResponse(loadProduct(productId));
    }

    @Transactional(readOnly = true)
    public ProductPriceResponse getPriceForCustomer(Long productId, User customer,
                                                    DiscountLookupPort discountLookupPort) {
        Product product = loadProduct(productId);
        BigDecimal bestDiscountPercent = discountLookupPort.findBestActiveDiscountPercent(customer.getId(), productId);
        BigDecimal effectivePrice = applyDiscount(product.getRecommendedRetailPrice(), bestDiscountPercent);
        return new ProductPriceResponse(productId, product.getRecommendedRetailPrice(), effectivePrice, bestDiscountPercent);
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest, User actingUser) {
        Product product = new Product();
        applyProductRequest(product, productRequest);
        Product savedProduct = productRepository.save(product);
        syncVariants(savedProduct, productRequest);
        Product savedWithVariants = productRepository.save(savedProduct);
        recordProductAction(actingUser, "CREATE_PRODUCT", savedWithVariants,
                "Product created: " + savedWithVariants.getName());
        return toResponse(savedWithVariants);
    }

    @Transactional
    public ProductResponse updateProduct(Long productId, ProductRequest productRequest, User actingUser) {
        Product product = loadProduct(productId);
        if (product.getParentProduct() != null) {
            throw new IllegalArgumentException("Variant products must be edited through their parent product.");
        }
        applyProductRequest(product, productRequest);
        syncVariants(product, productRequest);
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT", savedProduct,
                "Product updated: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public void deleteProduct(Long productId, User actingUser) {
        Product product = loadProduct(productId);
        productRepository.delete(product);
        recordProductAction(actingUser, "DELETE_PRODUCT", product,
                "Product deleted: " + product.getName());
    }

    @Transactional
    public ProductResponse setPurchasable(Long productId, boolean purchasable, User actingUser) {
        Product product = loadProduct(productId);
        product.setPurchasable(purchasable);
        product.getVariants().forEach(variant -> variant.setPurchasable(purchasable));
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "SET_PRODUCT_PURCHASABLE", savedProduct,
                "Product purchasable set to " + purchasable + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse setPromoted(Long productId, boolean promoted, User actingUser) {
        Product product = loadProduct(productId);
        product.setPromoted(promoted);
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "SET_PRODUCT_PROMOTED", savedProduct,
                "Product promoted set to " + promoted + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateDescription(Long productId, UpdateDescriptionRequest updateDescriptionRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setDescription(updateDescriptionRequest.description());
        product.getVariants().forEach(variant -> variant.setDescription(updateDescriptionRequest.description()));
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_DESCRIPTION", savedProduct,
                "Product description updated: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateImage(Long productId, UpdateImageRequest updateImageRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setImageUrl(updateImageRequest.imageUrl());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_IMAGE", savedProduct,
                "Product image updated: " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updatePrice(Long productId, UpdatePriceRequest updatePriceRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setRecommendedRetailPrice(updatePriceRequest.recommendedRetailPrice());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_PRICE", savedProduct,
                "Product price updated to " + updatePriceRequest.recommendedRetailPrice() + ": " + savedProduct.getName());
        return toResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateCo2Emission(Long productId, UpdateCo2EmissionRequest updateCo2EmissionRequest) {
        Product product = loadProduct(productId);
        product.setCo2EmissionKg(updateCo2EmissionRequest.co2EmissionKg());
        product.getVariants().forEach(variant -> variant.setCo2EmissionKg(updateCo2EmissionRequest.co2EmissionKg()));
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateEcoScore(Long productId, UpdateEcoScoreRequest updateEcoScoreRequest) {
        Product product = loadProduct(productId);
        product.setEcoScore(updateEcoScoreRequest.ecoScore());
        product.getVariants().forEach(variant -> variant.setEcoScore(updateEcoScoreRequest.ecoScore()));
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateStock(Long productId, UpdateStockRequest updateStockRequest, User actingUser) {
        Product product = loadProduct(productId);
        product.setStock(updateStockRequest.stock());
        Product savedProduct = productRepository.save(product);
        recordProductAction(actingUser, "UPDATE_PRODUCT_STOCK", savedProduct,
                "Product stock updated to " + updateStockRequest.stock() + ": " + savedProduct.getName());
        return toResponse(savedProduct);
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
                product.getProductType(),
                product.getStock(),
                product.getSku(),
                product.isPurchasable(),
                product.isPromoted(),
                product.isPersonalizable(),
                product.getPersonalizationMaxLength(),
                product.isHasVariants(),
                product.getParentProduct() == null ? null : product.getParentProduct().getId(),
                toVariantValues(product),
                product.getParentProduct() == null ? toAttributeResponses(product) : List.of(),
                product.getParentProduct() == null
                        ? product.getVariants().stream().map(this::toResponse).toList()
                        : List.of(),
                product.getCreatedAt()
        );
    }

    private void applyProductRequest(Product product, ProductRequest productRequest) {
        product.setName(productRequest.name().trim());
        product.setDescription(trimToNull(productRequest.description()));
        product.setImageUrl(trimToNull(productRequest.imageUrl()));
        product.setRecommendedRetailPrice(productRequest.recommendedRetailPrice());
        product.setCo2EmissionKg(productRequest.co2EmissionKg());
        product.setEcoScore(productRequest.ecoScore() == null ? ProductEcoScore.NONE : productRequest.ecoScore());
        product.setCategory(trimToNull(productRequest.category()));
        product.setStock(productRequest.stock() == null ? product.getStock() : productRequest.stock());
        product.setSku(trimToNull(productRequest.sku()));
        if (productRequest.purchasable() != null) {
            product.setPurchasable(productRequest.purchasable());
        }
        boolean personalizable = Boolean.TRUE.equals(productRequest.personalizable());
        product.setPersonalizable(personalizable);
        product.setPersonalizationMaxLength(resolvePersonalizationMaxLength(personalizable, productRequest.personalizationMaxLength()));
        product.setHasVariants(productRequest.hasVariants());
    }

    private void syncVariants(Product parent, ProductRequest productRequest) {
        if (!productRequest.hasVariants()) {
            parent.getVariantAttributes().clear();
            parent.getVariants().clear();
            return;
        }

        List<NormalizedAttribute> attributes = normalizeAttributes(productRequest.variantAttributes());
        if (attributes.isEmpty()) {
            parent.getVariantAttributes().clear();
            parent.getVariants().clear();
            parent.setHasVariants(false);
            return;
        }

        replaceVariantAttributes(parent, attributes);
        List<ProductVariantRequest> requestedVariants = normalizeVariantRequests(productRequest, attributes);
        Set<String> desiredKeys = requestedVariants.stream()
                .map(variant -> signature(normalizeVariantValues(variant.values(), attributes)))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        parent.getVariants().removeIf(variant -> !desiredKeys.contains(signature(toVariantValues(variant))));
        Map<Long, Product> existingById = new LinkedHashMap<>();
        Map<String, Product> existingBySignature = new LinkedHashMap<>();
        for (Product variant : parent.getVariants()) {
            existingById.put(variant.getId(), variant);
            existingBySignature.put(signature(toVariantValues(variant)), variant);
        }

        int index = 1;
        for (ProductVariantRequest variantRequest : requestedVariants) {
            Map<String, String> values = normalizeVariantValues(variantRequest.values(), attributes);
            Product variant = variantRequest.id() == null ? null : existingById.get(variantRequest.id());
            if (variant == null) {
                variant = existingBySignature.get(signature(values));
            }
            if (variant == null) {
                variant = new Product();
                variant.setParentProduct(parent);
                parent.getVariants().add(variant);
            }
            applyVariant(parent, variant, variantRequest, values, index++);
        }
    }

    private void replaceVariantAttributes(Product parent, List<NormalizedAttribute> attributes) {
        parent.getVariantAttributes().clear();
        int attributeIndex = 0;
        for (NormalizedAttribute normalizedAttribute : attributes) {
            ProductVariantAttribute attribute = new ProductVariantAttribute();
            attribute.setProduct(parent);
            attribute.setName(normalizedAttribute.name());
            attribute.setDisplayOrder(attributeIndex++);
            int valueIndex = 0;
            for (String value : normalizedAttribute.values()) {
                ProductVariantAttributeValue attributeValue = new ProductVariantAttributeValue();
                attributeValue.setAttribute(attribute);
                attributeValue.setValue(value);
                attributeValue.setDisplayOrder(valueIndex++);
                attribute.getValues().add(attributeValue);
            }
            parent.getVariantAttributes().add(attribute);
        }
    }

    private void applyVariant(Product parent, Product variant, ProductVariantRequest variantRequest,
                              Map<String, String> values, int index) {
        variant.setName(parent.getName() + " - " + String.join(" / ", values.values()));
        variant.setDescription(parent.getDescription());
        variant.setImageUrl(firstNonBlank(variantRequest.imageUrl(), parent.getImageUrl()));
        variant.setRecommendedRetailPrice(variantRequest.recommendedRetailPrice() == null
                ? parent.getRecommendedRetailPrice()
                : variantRequest.recommendedRetailPrice());
        variant.setCo2EmissionKg(parent.getCo2EmissionKg());
        variant.setEcoScore(parent.getEcoScore());
        variant.setCategory(parent.getCategory());
        variant.setStock(variantRequest.stock() == null ? parent.getStock() : variantRequest.stock());
        variant.setSku(firstNonBlank(variantRequest.sku(), buildGeneratedSku(parent, index)));
        variant.setPurchasable(parent.isPurchasable());
        variant.setPromoted(false);
        variant.setPersonalizable(parent.isPersonalizable());
        variant.setPersonalizationMaxLength(parent.getPersonalizationMaxLength());
        variant.setHasVariants(false);

        variant.getVariantOptions().clear();
        int optionIndex = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ProductVariantOption option = new ProductVariantOption();
            option.setProduct(variant);
            option.setAttributeName(entry.getKey());
            option.setAttributeValue(entry.getValue());
            option.setDisplayOrder(optionIndex++);
            variant.getVariantOptions().add(option);
        }
    }

    private List<ProductVariantRequest> normalizeVariantRequests(ProductRequest productRequest,
                                                                 List<NormalizedAttribute> attributes) {
        if (productRequest.variants() != null && !productRequest.variants().isEmpty()) {
            return productRequest.variants().stream()
                    .filter(Objects::nonNull)
                    .filter(variant -> !normalizeVariantValues(variant.values(), attributes).isEmpty())
                    .toList();
        }

        List<Map<String, String>> combinations = buildCombinations(attributes, 0, new LinkedHashMap<>());
        List<ProductVariantRequest> generatedVariants = new ArrayList<>();
        int index = 1;
        for (Map<String, String> values : combinations) {
            generatedVariants.add(new ProductVariantRequest(
                    null,
                    buildGeneratedSkuFromName(productRequest.sku(), productRequest.name(), index++),
                    productRequest.recommendedRetailPrice(),
                    productRequest.stock() == null ? 25 : productRequest.stock(),
                    productRequest.imageUrl(),
                    values
            ));
        }
        return generatedVariants;
    }

    private List<Map<String, String>> buildCombinations(List<NormalizedAttribute> attributes, int attributeIndex,
                                                        LinkedHashMap<String, String> current) {
        if (attributeIndex >= attributes.size()) {
            return List.of(new LinkedHashMap<>(current));
        }

        List<Map<String, String>> combinations = new ArrayList<>();
        NormalizedAttribute attribute = attributes.get(attributeIndex);
        for (String value : attribute.values()) {
            current.put(attribute.name(), value);
            combinations.addAll(buildCombinations(attributes, attributeIndex + 1, current));
        }
        current.remove(attribute.name());
        return combinations;
    }

    private List<NormalizedAttribute> normalizeAttributes(List<ProductVariantAttributeRequest> attributes) {
        if (attributes == null) {
            return List.of();
        }

        List<NormalizedAttribute> normalizedAttributes = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (ProductVariantAttributeRequest attribute : attributes) {
            if (attribute == null || trimToNull(attribute.name()) == null) {
                continue;
            }
            String name = attribute.name().trim();
            if (!seenNames.add(name.toLowerCase())) {
                continue;
            }
            List<String> values = normalizeValues(attribute.values());
            if (!values.isEmpty()) {
                normalizedAttributes.add(new NormalizedAttribute(name, values));
            }
        }
        return normalizedAttributes;
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> normalizedValues = new ArrayList<>();
        Set<String> seenValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = trimToNull(value);
            if (normalizedValue != null && seenValues.add(normalizedValue.toLowerCase())) {
                normalizedValues.add(normalizedValue);
            }
        }
        return normalizedValues;
    }

    private Map<String, String> normalizeVariantValues(Map<String, String> values, List<NormalizedAttribute> attributes) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalizedValues = new LinkedHashMap<>();
        for (NormalizedAttribute attribute : attributes) {
            String value = values.get(attribute.name());
            if (trimToNull(value) == null || !attribute.values().contains(value.trim())) {
                return Map.of();
            }
            normalizedValues.put(attribute.name(), value.trim());
        }
        return normalizedValues;
    }

    private List<ProductVariantAttributeResponse> toAttributeResponses(Product product) {
        return product.getVariantAttributes().stream()
                .map(attribute -> new ProductVariantAttributeResponse(
                        attribute.getName(),
                        attribute.getValues().stream()
                                .map(ProductVariantAttributeValue::getValue)
                                .toList()))
                .toList();
    }

    private Map<String, String> toVariantValues(Product product) {
        Map<String, String> values = new LinkedHashMap<>();
        product.getVariantOptions().stream()
                .sorted((left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()))
                .forEach(option -> values.put(option.getAttributeName(), option.getAttributeValue()));
        return values;
    }

    private String signature(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> entry.getKey().toLowerCase() + "=" + entry.getValue().toLowerCase())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
    }

    private String buildGeneratedSku(Product parent, int index) {
        return buildGeneratedSkuFromName(parent.getSku(), parent.getName(), index);
    }

    private String buildGeneratedSkuFromName(String sku, String name, int index) {
        String base = firstNonBlank(sku, name);
        if (base == null) {
            base = "PRODUCT";
        }
        String normalizedBase = base.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return (normalizedBase.isBlank() ? "PRODUCT" : normalizedBase) + "-V" + index;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Integer resolvePersonalizationMaxLength(boolean personalizable, Integer personalizationMaxLength) {
        if (!personalizable) {
            return null;
        }
        if (personalizationMaxLength == null || personalizationMaxLength <= 0) {
            throw new IllegalArgumentException("Die maximale Zeichenlaenge fuer Personalisierung muss groesser als 0 sein.");
        }
        if (personalizationMaxLength > 1000) {
            throw new IllegalArgumentException("Die maximale Zeichenlaenge fuer Personalisierung darf hoechstens 1000 betragen.");
        }
        return personalizationMaxLength;
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

    private record NormalizedAttribute(String name, List<String> values) {}

    @FunctionalInterface
    public interface DiscountLookupPort {
        BigDecimal findBestActiveDiscountPercent(Long customerId, Long productId);
    }
}
