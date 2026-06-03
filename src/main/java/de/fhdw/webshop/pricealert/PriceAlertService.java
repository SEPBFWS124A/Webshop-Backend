package de.fhdw.webshop.pricealert;

import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.notification.SystemNotificationService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceAlertService {

    private final PriceAlertRepository priceAlertRepository;
    private final ProductService productService;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final SystemNotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public PriceAlertResponse createPriceAlert(Long productId, CreatePriceAlertRequest request, User user) {
        Product product = productService.loadProduct(productId);

        if (!product.isPurchasable()) {
            throw new IllegalArgumentException("Produkt ist nicht kaufbar.");
        }

        if (request.targetPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetPrice muss größer als 0 sein.");
        }

        if (priceAlertRepository.existsDuplicate(user.getId(), productId, request.targetPrice())) {
            throw new DuplicatePriceAlertException("Ein aktiver Preisalarm mit identischem Zielpreis existiert bereits für dieses Produkt.");
        }

        PriceAlert alert = new PriceAlert();
        alert.setUser(user);
        alert.setProduct(product);
        alert.setTargetPrice(request.targetPrice());
        alert.setNotifyByEmail(request.notifyByEmail());

        PriceAlert saved = priceAlertRepository.save(alert);

        BigDecimal currentPrice = getEffectivePriceForCustomer(productId, user.getId());
        return toResponse(saved, currentPrice);
    }

    @Transactional(readOnly = true)
    public List<PriceAlertResponse> getAlertsForUser(User user) {
        List<PriceAlert> alerts = priceAlertRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return alerts.stream()
                .map(alert -> {
                    BigDecimal currentPrice = getEffectivePriceForCustomer(
                            alert.getProduct().getId(), user.getId());
                    return toResponse(alert, currentPrice);
                })
                .toList();
    }

    @Transactional
    public PriceAlertResponse updatePriceAlert(Long alertId, UpdatePriceAlertRequest request, User user) {
        PriceAlert alert = loadOwnedAlert(alertId, user);

        if (request.targetPrice() != null) {
            if (request.targetPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("targetPrice muss größer als 0 sein.");
            }
            alert.setTargetPrice(request.targetPrice());
        }

        if (request.notifyByEmail() != null) {
            alert.setNotifyByEmail(request.notifyByEmail());
        }

        if (request.active() != null) {
            alert.setActive(request.active());
            if (!request.active()) {
                alert.setStatus(PriceAlertStatus.DISABLED);
            } else if (alert.getStatus() == PriceAlertStatus.DISABLED) {
                alert.setStatus(PriceAlertStatus.ACTIVE);
            }
        }

        PriceAlert saved = priceAlertRepository.save(alert);
        BigDecimal currentPrice = getEffectivePriceForCustomer(alert.getProduct().getId(), user.getId());
        return toResponse(saved, currentPrice);
    }

    @Transactional
    public void deletePriceAlert(Long alertId, User user) {
        PriceAlert alert = loadOwnedAlert(alertId, user);
        priceAlertRepository.delete(alert);
    }

    /**
     * Scheduler: Prüft alle 15 Minuten aktive Preisalarme.
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void checkPriceAlerts() {
        List<PriceAlert> activeAlerts = priceAlertRepository.findByStatusAndActiveTrue(PriceAlertStatus.ACTIVE);
        log.debug("Prüfe {} aktive Preisalarme", activeAlerts.size());

        for (PriceAlert alert : activeAlerts) {
            try {
                BigDecimal currentPrice = getEffectivePriceForCustomer(
                        alert.getProduct().getId(),
                        alert.getUser().getId()
                );

                alert.setLastCheckedAt(Instant.now());

                if (currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                    triggerAlert(alert, currentPrice);
                }

                priceAlertRepository.save(alert);
            } catch (Exception e) {
                log.error("Fehler bei Prüfung von Preisalarm {}: {}", alert.getId(), e.getMessage());
            }
        }
    }

    /**
     * Event-basierter Trigger: Prüft Alarme für ein bestimmtes Produkt bei Preisänderung.
     */
    @Transactional
    public void checkAlertsForProduct(Long productId) {
        List<PriceAlert> affectedAlerts = priceAlertRepository
                .findByProductIdAndStatusAndActiveTrue(productId, PriceAlertStatus.ACTIVE);

        for (PriceAlert alert : affectedAlerts) {
            try {
                BigDecimal currentPrice = getEffectivePriceForCustomer(productId, alert.getUser().getId());
                alert.setLastCheckedAt(Instant.now());

                if (currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                    triggerAlert(alert, currentPrice);
                }

                priceAlertRepository.save(alert);
            } catch (Exception e) {
                log.error("Fehler bei event-basierter Prüfung von Preisalarm {}: {}", alert.getId(), e.getMessage());
            }
        }
    }

    private void triggerAlert(PriceAlert alert, BigDecimal currentPrice) {
        alert.setStatus(PriceAlertStatus.TRIGGERED);
        alert.setTriggeredAt(Instant.now());
        alert.setActive(false);

        // In-App-Benachrichtigung
        String message = buildTriggerMessage(alert, currentPrice);
        notificationService.createPriceAlertNotification(
                alert.getUser(),
                alert.getProduct().getId(),
                alert.getProduct().getName(),
                message,
                "/products/" + alert.getProduct().getId()
        );

        // Optional: E-Mail
        if (alert.isNotifyByEmail()) {
            sendPriceAlertEmail(alert, currentPrice);
        }

        log.info("Preisalarm {} ausgelöst für User {} – Produkt '{}' bei {}",
                alert.getId(), alert.getUser().getId(), alert.getProduct().getName(), currentPrice);
    }

    private String buildTriggerMessage(PriceAlert alert, BigDecimal currentPrice) {
        return String.format(
                "Preisalarm: \"%s\" hat deinen Wunschpreis erreicht! Aktueller Preis: %s € (Zielpreis: %s €)",
                alert.getProduct().getName(),
                formatPrice(currentPrice),
                formatPrice(alert.getTargetPrice())
        );
    }

    private void sendPriceAlertEmail(PriceAlert alert, BigDecimal currentPrice) {
        String subject = "Dein Preisalarm wurde ausgelöst – " + alert.getProduct().getName();
        String body = String.format(
                "Hallo,\n\n" +
                "dein Preisalarm wurde ausgelöst!\n\n" +
                "Produkt: %s\n" +
                "Aktueller Preis: %s €\n" +
                "Dein Zielpreis: %s €\n\n" +
                "Schau jetzt vorbei und sichere dir das Angebot!\n\n" +
                "Viele Grüße,\nDein Webshop-Team",
                alert.getProduct().getName(),
                formatPrice(currentPrice),
                formatPrice(alert.getTargetPrice())
        );
        emailService.sendEmail(alert.getUser().getEmail(), subject, body);
    }

    private BigDecimal getEffectivePriceForCustomer(Long productId, Long userId) {
        Product product = productService.loadProduct(productId);
        BigDecimal discountPercent = discountLookupPort.findBestActiveDiscountPercent(userId, productId);
        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return product.getRecommendedRetailPrice();
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        return product.getRecommendedRetailPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private PriceAlert loadOwnedAlert(Long alertId, User user) {
        PriceAlert alert = priceAlertRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Preisalarm nicht gefunden: " + alertId));
        if (!alert.getUser().getId().equals(user.getId())) {
            throw new EntityNotFoundException("Preisalarm nicht gefunden: " + alertId);
        }
        return alert;
    }

    private PriceAlertResponse toResponse(PriceAlert alert, BigDecimal currentPrice) {
        return new PriceAlertResponse(
                alert.getId(),
                alert.getProduct().getId(),
                alert.getProduct().getName(),
                alert.getTargetPrice(),
                currentPrice,
                alert.isActive(),
                alert.getStatus(),
                alert.isNotifyByEmail(),
                alert.getCreatedAt(),
                alert.getTriggeredAt()
        );
    }

    private String formatPrice(BigDecimal price) {
        return String.format("%.2f", price).replace('.', ',');
    }
}



