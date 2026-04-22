package de.fhdw.webshop.notification;

import de.fhdw.webshop.alerting.AlertEventType;
import de.fhdw.webshop.alerting.BusinessEmailService;

import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * US #35 — Notifies sales employees when a product's sales drop by more than 20% week-over-week.
 * US #36 — Notifies sales employees when a purchasable product had zero sales in the past 30 days.
 * #22    — Sends an email alert when the HTTP 5xx error count in 15 minutes exceeds a threshold.
 * #23    — Sends an email alert when JVM heap usage exceeds a configured percentage threshold.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final BusinessEmailService businessEmailService;
    private final MeterRegistry meterRegistry;

    @Value("${alert.error-rate.threshold:10}")
    private int errorRateThreshold;

    @Value("${alert.heap-usage.threshold-percent:85}")
    private int heapUsageThresholdPercent;

    private final AtomicReference<Double> lastErrorCountSnapshot = new AtomicReference<>(null);

    @Scheduled(cron = "0 0 7 * * MON")
    public void checkSalesAlerts() {
        checkZeroSalesProducts();
        checkSignificantSalesDrop();
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void checkHighErrorRate() {
        Counter errorCounter = Search.in(meterRegistry)
                .name("http.server.requests")
                .tag("outcome", "SERVER_ERROR")
                .counter();

        if (errorCounter == null) {
            return;
        }

        double currentCount = errorCounter.count();
        Double previousCount = lastErrorCountSnapshot.getAndSet(currentCount);

        if (previousCount == null) {
            return;
        }

        double errorsInInterval = currentCount - previousCount;
        log.info("Monitoring: {} HTTP 5xx errors in last 15 minutes (threshold: {})",
                (int) errorsInInterval, errorRateThreshold);

        if (errorsInInterval >= errorRateThreshold) {
            String subject = "Alert: High HTTP error rate detected";
            String body = String.format(
                    "The webshop backend recorded %d HTTP 5xx errors in the last 15 minutes.%n"
                    + "Threshold: %d errors per 15 minutes.%n%n"
                    + "Please check the application logs and Grafana dashboard.",
                    (int) errorsInInterval, errorRateThreshold);
            try {
                businessEmailService.sendAlert(AlertEventType.HIGH_ERROR_RATE, subject, body);
                log.warn("Alert sent: {} HTTP 5xx errors exceeded threshold of {}", (int) errorsInInterval, errorRateThreshold);
            } catch (Exception exception) {
                log.error("Failed to send high error rate alert email", exception);
            }
        }
    }

    @Scheduled(cron = "0 0/30 * * * *")
    public void checkJvmHeapUsage() {
        Gauge usedGauge = Search.in(meterRegistry)
                .name("jvm.memory.used")
                .tag("area", "heap")
                .gauge();
        Gauge maxGauge = Search.in(meterRegistry)
                .name("jvm.memory.max")
                .tag("area", "heap")
                .gauge();

        if (usedGauge == null || maxGauge == null) {
            return;
        }

        double usedBytes = usedGauge.value();
        double maxBytes = maxGauge.value();

        if (maxBytes <= 0) {
            return;
        }

        int usedPercent = (int) (100.0 * usedBytes / maxBytes);
        long usedMb = (long) (usedBytes / 1_048_576);
        long maxMb = (long) (maxBytes / 1_048_576);

        log.info("Monitoring: JVM heap usage {}% ({} MB / {} MB, threshold: {}%)",
                usedPercent, usedMb, maxMb, heapUsageThresholdPercent);

        if (usedPercent >= heapUsageThresholdPercent) {
            String subject = "Alert: High JVM heap usage detected";
            String body = String.format(
                    "JVM heap usage is at %d%% (%d MB of %d MB used).%n"
                    + "Threshold: %d%%.%n%n"
                    + "This may indicate a memory leak. Please check the Grafana JVM dashboard.",
                    usedPercent, usedMb, maxMb, heapUsageThresholdPercent);
            try {
                businessEmailService.sendAlert(AlertEventType.HIGH_HEAP_USAGE, subject, body);
                log.warn("Alert sent: JVM heap at {}% exceeded threshold of {}%", usedPercent, heapUsageThresholdPercent);
            } catch (Exception exception) {
                log.error("Failed to send JVM heap alert email", exception);
            }
        }
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
