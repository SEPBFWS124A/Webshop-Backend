package de.fhdw.webshop.pricehistory;

import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPriceHistoryService {

    private final ProductPriceHistoryRepository historyRepository;
    private final ProductRepository productRepository;

    /**
     * Protokolliert einen initialen Preis-Eintrag bei Produktanlage.
     */
    @Transactional
    public void recordInitialPrice(Product product, User changedBy) {
        ProductPriceHistory entry = new ProductPriceHistory();
        entry.setProduct(product);
        entry.setOldPrice(null);
        entry.setNewPrice(product.getRecommendedRetailPrice());
        entry.setChangeReason(PriceChangeReason.INITIAL);
        entry.setChangedBy(changedBy);
        historyRepository.save(entry);
        log.debug("Initialer Preiseintrag für Produkt {} gespeichert: {}", product.getId(), product.getRecommendedRetailPrice());
    }

    /**
     * Protokolliert eine Preisänderung – kein Eintrag wenn Preis identisch.
     */
    @Transactional
    public void recordPriceChange(Product product, BigDecimal oldPrice, BigDecimal newPrice,
                                   PriceChangeReason reason, User changedBy) {
        if (newPrice == null) return;
        if (oldPrice != null && oldPrice.compareTo(newPrice) == 0) {
            log.debug("Preis für Produkt {} unverändert ({}), kein Historieneintrag", product.getId(), newPrice);
            return;
        }
        ProductPriceHistory entry = new ProductPriceHistory();
        entry.setProduct(product);
        entry.setOldPrice(oldPrice);
        entry.setNewPrice(newPrice);
        entry.setChangeReason(reason);
        entry.setChangedBy(changedBy);
        historyRepository.save(entry);
        log.info("Preisänderung für Produkt {} gespeichert: {} → {}", product.getId(), oldPrice, newPrice);
    }

    /**
     * Liefert den Preisverlauf als öffentliche Ansicht (nur changedAt + newPrice).
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryPublicDto> getPublicHistory(Long productId, Instant from, Instant to, int limit) {
        ensureProductExists(productId);
        List<ProductPriceHistory> entries = historyRepository
                .findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(productId, from, to);
        return entries.stream()
                .limit(limit)
                .map(e -> new PriceHistoryPublicDto(e.getChangedAt(), e.getNewPrice()))
                .toList();
    }

    /**
     * Liefert den Preisverlauf als Admin-Ansicht (alle Felder).
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryAdminDto> getAdminHistory(Long productId, Instant from, Instant to, int limit) {
        ensureProductExists(productId);
        List<ProductPriceHistory> entries = historyRepository
                .findByProductIdAndChangedAtBetweenOrderByChangedAtAsc(productId, from, to);
        return entries.stream()
                .limit(limit)
                .map(e -> new PriceHistoryAdminDto(
                        e.getId(),
                        e.getChangedAt(),
                        e.getOldPrice(),
                        e.getNewPrice(),
                        e.getChangeReason(),
                        e.getChangedBy() != null ? e.getChangedBy().getUsername() : null
                ))
                .toList();
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new EntityNotFoundException("Produkt nicht gefunden: " + productId);
        }
    }
}
