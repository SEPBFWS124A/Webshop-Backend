package de.fhdw.webshop.user.recentlyviewed;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.recentlyviewed.dto.RecentlyViewedProductResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecentlyViewedProductService {

    private static final int MAX_RECENTLY_VIEWED_PRODUCTS = 20;

    private final RecentlyViewedProductRepository recentlyViewedProductRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public List<RecentlyViewedProductResponse> listForUser(User currentUser, Boolean marketplace) {
        List<RecentlyViewedProduct> entries;
        if (Boolean.TRUE.equals(marketplace)) {
            entries = recentlyViewedProductRepository.findMarketplaceByUserIdOrderByViewedAtDesc(currentUser.getId());
        } else if (Boolean.FALSE.equals(marketplace)) {
            entries = recentlyViewedProductRepository.findShopByUserIdOrderByViewedAtDesc(currentUser.getId());
        } else {
            entries = recentlyViewedProductRepository.findByUserIdOrderByViewedAtDesc(currentUser.getId());
        }
        return entries.stream()
                .limit(MAX_RECENTLY_VIEWED_PRODUCTS)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void recordView(User currentUser, Long productId) {
        User managedUser = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUser.getId()));
        Product product = productService.loadProduct(productId);

        RecentlyViewedProduct recentlyViewedProduct = recentlyViewedProductRepository
                .findByUserIdAndProductId(managedUser.getId(), product.getId())
                .orElseGet(RecentlyViewedProduct::new);

        recentlyViewedProduct.setUser(managedUser);
        recentlyViewedProduct.setProduct(product);
        recentlyViewedProduct.setViewedAt(Instant.now());
        recentlyViewedProductRepository.save(recentlyViewedProduct);

        List<RecentlyViewedProduct> allEntries = recentlyViewedProductRepository.findByUserIdOrderByViewedAtDesc(managedUser.getId());
        if (allEntries.size() > MAX_RECENTLY_VIEWED_PRODUCTS) {
            recentlyViewedProductRepository.deleteAll(allEntries.subList(MAX_RECENTLY_VIEWED_PRODUCTS, allEntries.size()));
        }
    }

    private RecentlyViewedProductResponse toResponse(RecentlyViewedProduct recentlyViewedProduct) {
        Product product = recentlyViewedProduct.getProduct();
        return new RecentlyViewedProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getCategory(),
                product.getRecommendedRetailPrice(),
                product.isPurchasable(),
                product.getSellerName(),
                recentlyViewedProduct.getViewedAt()
        );
    }
}
