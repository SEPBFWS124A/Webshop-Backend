package de.fhdw.webshop.notification;

import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * US #35 — Notifies sales employees when a product's sales drop by more than 20% week-over-week.
 * US #36 — Notifies sales employees when a purchasable product had zero sales in the past 30 days.
 * Runs every Monday at 07:00.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Scheduled(cron = "0 0 7 * * MON")
    public void checkSalesAlerts() {
        checkZeroSalesProducts();
        checkSignificantSalesDrop();
    }

    /** US #36 — Log an alert for every purchasable product with zero sales in the last 30 days. */
    private void checkZeroSalesProducts() {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        Instant fromInstant = thirtyDaysAgo.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = Instant.now();

        List<Product> purchasableProducts = productRepository.searchProducts(true, "", "");
        for (Product product : purchasableProducts) {
            List<OrderItem> recentSales = orderRepository
                    .findOrderItemsByProductIdAndDateRange(product.getId(), fromInstant, toInstant);
            if (recentSales.isEmpty()) {
                log.warn("ALERT [US#36]: Product '{}' (id={}) had zero sales in the last 30 days",
                        product.getName(), product.getId());
            }
        }
    }

    /** US #35 — Log an alert when a product's sales dropped by more than 20% compared to the previous week. */
    private void checkSignificantSalesDrop() {
        LocalDate today = LocalDate.now();
        Instant currentWeekStart = today.minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant previousWeekStart = today.minusDays(14).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant currentWeekEnd = Instant.now();
        Instant previousWeekEnd = today.minusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Product> purchasableProducts = productRepository.searchProducts(true, "", "");
        for (Product product : purchasableProducts) {
            long currentWeekUnits = orderRepository
                    .findOrderItemsByProductIdAndDateRange(product.getId(), currentWeekStart, currentWeekEnd)
                    .stream().mapToLong(OrderItem::getQuantity).sum();
            long previousWeekUnits = orderRepository
                    .findOrderItemsByProductIdAndDateRange(product.getId(), previousWeekStart, previousWeekEnd)
                    .stream().mapToLong(OrderItem::getQuantity).sum();

            if (previousWeekUnits > 0) {
                double changePercent = ((double) (previousWeekUnits - currentWeekUnits) / previousWeekUnits) * 100;
                if (changePercent > 20.0) {
                    log.warn("ALERT [US#35]: Product '{}' (id={}) sales dropped by {}% week-over-week",
                            product.getName(), product.getId(), String.format("%.1f", changePercent));
                }
            }
        }
    }
}
