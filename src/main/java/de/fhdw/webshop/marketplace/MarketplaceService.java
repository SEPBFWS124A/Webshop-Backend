package de.fhdw.webshop.marketplace;

import de.fhdw.webshop.marketplace.dto.MarketplaceProductDto;
import de.fhdw.webshop.marketplace.dto.MarketplaceReviewDto;
import de.fhdw.webshop.marketplace.dto.MarketplaceSellerDto;
import de.fhdw.webshop.marketplace.dto.MarketplaceSellerProfileDto;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.sellerportal.SellerProfileRepository;
import de.fhdw.webshop.sellerreview.SellerReview;
import de.fhdw.webshop.sellerreview.SellerReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalDouble;

@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final SellerReviewRepository sellerReviewRepository;

    @Transactional(readOnly = true)
    public List<MarketplaceProductDto> listProducts(String category, String sellerName) {
        String cat = category != null ? category : "";
        String seller = sellerName != null ? sellerName : "";
        return productRepository.searchMarketplaceProducts(cat, seller)
                .stream()
                .map(this::toProductDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceSellerDto> listSellers() {
        return sellerProfileRepository.findAllByActiveTrueOrderByDisplayNameAsc()
                .stream()
                .map(profile -> {
                    List<SellerReview> reviews = sellerReviewRepository.findBySellerNameIgnoreCaseOrderByCreatedAtDesc(profile.getDisplayName());
                    double avg = reviews.stream().mapToInt(SellerReview::getRating).average().orElse(0.0);
                    return new MarketplaceSellerDto(profile.getDisplayName(), profile.getDisplayName(), avg, reviews.size());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceSellerProfileDto getSellerProfile(String sellerName) {
        sellerProfileRepository.findByDisplayNameIgnoreCase(sellerName)
                .orElseThrow(() -> new EntityNotFoundException("Seller not found: " + sellerName));

        List<MarketplaceProductDto> products = productRepository.searchMarketplaceProducts("", sellerName)
                .stream()
                .map(this::toProductDto)
                .toList();

        List<SellerReview> reviews = sellerReviewRepository.findBySellerNameIgnoreCaseOrderByCreatedAtDesc(sellerName);
        OptionalDouble avg = reviews.stream().mapToInt(SellerReview::getRating).average();

        List<MarketplaceReviewDto> recentReviews = reviews.stream()
                .limit(10)
                .map(r -> new MarketplaceReviewDto(r.getId(), r.getRating(), r.getComment(), r.getCreatedAt()))
                .toList();

        return new MarketplaceSellerProfileDto(
                sellerName,
                sellerName,
                avg.orElse(0.0),
                reviews.size(),
                products,
                recentReviews
        );
    }

    private MarketplaceProductDto toProductDto(Product p) {
        return new MarketplaceProductDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getImageUrl(),
                p.getRecommendedRetailPrice(),
                p.getCategory(),
                p.getSellerName(),
                p.getEcoScore(),
                p.getStock()
        );
    }
}
