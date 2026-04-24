package de.fhdw.webshop.standingorder;

import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.recommendation.ProductRecommendationService;
import de.fhdw.webshop.recommendation.dto.ProductRecommendationItemResponse;
import de.fhdw.webshop.recommendation.dto.ProductRecommendationListResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StandingOrderAvailabilityNotificationService {

    private final StandingOrderRepository standingOrderRepository;
    private final ProductRecommendationService productRecommendationService;
    private final EmailService emailService;

    @Transactional
    public void checkAndNotify() {
        List<StandingOrder> activeOrders = standingOrderRepository.findByActiveIsTrue();
        for (StandingOrder so : activeOrders) {
            try {
                processStandingOrder(so);
            } catch (Exception e) {
                log.error("Fehler beim Verfügbarkeitscheck für Dauerauftrag {}: {}", so.getId(), e.getMessage());
            }
        }
    }

    private void processStandingOrder(StandingOrder so) {
        User customer = so.getCustomer();
        List<StandingOrderItem> newlyUnavailableItems = new ArrayList<>();

        for (StandingOrderItem item : so.getItems()) {
            Product product = item.getProduct();
            boolean unavailable = !product.isPurchasable() || product.getStock() <= 0;

            if (unavailable && !item.isNotifiedUnavailable()) {
                item.setNotifiedUnavailable(true);
                newlyUnavailableItems.add(item);
            } else if (!unavailable && item.isNotifiedUnavailable()) {
                item.setNotifiedUnavailable(false);
            }
        }

        if (!newlyUnavailableItems.isEmpty()) {
            sendNotificationEmail(customer, so, newlyUnavailableItems);
        }
    }

    private void sendNotificationEmail(User customer, StandingOrder so, List<StandingOrderItem> unavailableItems) {
        String subject = "Produktverfügbarkeit in Ihrem Dauerauftrag hat sich geändert";
        String body = buildEmailBody(customer, so, unavailableItems);
        emailService.sendEmailToCustomer(customer, subject, body);
        log.info("Verfügbarkeits-Benachrichtigung für Dauerauftrag {} an {} gesendet ({} Produkt(e))",
                so.getId(), customer.getEmail(), unavailableItems.size());
    }

    private String buildEmailBody(User customer, StandingOrder so, List<StandingOrderItem> unavailableItems) {
        StringBuilder body = new StringBuilder();
        body.append("Hallo ").append(customer.getUsername()).append(",\n\n");
        body.append("in Ihrem Dauerauftrag #").append(so.getId())
                .append(" ist ein oder mehrere Produkte nicht mehr verfügbar.\n\n");

        for (StandingOrderItem item : unavailableItems) {
            Product product = item.getProduct();
            body.append("Nicht mehr verfügbar: ").append(product.getName()).append("\n");
            body.append(buildRecommendationsSection(product, customer));
            body.append("\n");
        }

        body.append("Bitte aktualisieren Sie Ihren Dauerauftrag im Webshop.\n\n");
        body.append("Mit freundlichen Grüßen,\nIhr Webshop-Team");
        return body.toString();
    }

    private String buildRecommendationsSection(Product product, User customer) {
        StringBuilder section = new StringBuilder();
        try {
            ProductRecommendationListResponse recommendations =
                    productRecommendationService.getRecommendationsForProduct(product.getId(), customer, 3);
            List<ProductRecommendationItemResponse> items = recommendations.recommendations();
            if (items.isEmpty()) {
                return "";
            }
            section.append("KI-Empfehlungen als Ersatz:\n");
            for (int i = 0; i < items.size(); i++) {
                ProductRecommendationItemResponse rec = items.get(i);
                BigDecimal price = rec.product().recommendedRetailPrice();
                section.append("  ").append(i + 1).append(". ")
                        .append(rec.product().name())
                        .append(" — ").append(price != null ? price + " EUR" : "Preis auf Anfrage")
                        .append("\n     ").append(rec.reason()).append("\n");
            }
        } catch (Exception e) {
            log.warn("KI-Empfehlungen für Produkt {} konnten nicht geladen werden: {}", product.getId(), e.getMessage());
        }
        return section.toString();
    }
}
