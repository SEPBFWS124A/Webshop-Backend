package de.fhdw.webshop.notification;

import de.fhdw.webshop.alerting.AlertEventType;
import de.fhdw.webshop.alerting.BusinessEmailService;

import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * US #90 — Automatisierte monatliche Verkaufsüberwachung.
 * Läuft am 1. jeden Monats um 07:00 Uhr (konfigurierbar).
 * Vergleicht die letzten N Tage mit dem vorherigen Zeitraum.
 * Erstellt Datenbankeinträge und verschickt einen gebündelten E-Mail-Digest.
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
    private final SystemNotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final BusinessEmailService businessEmailService;
    private final MeterRegistry meterRegistry;

    @Value("${alert.error-rate.threshold:10}")
    private int errorRateThreshold;

    @Value("${alert.heap-usage.threshold-percent:85}")
    private int heapUsageThresholdPercent;

    private final AtomicReference<Double> lastErrorCountSnapshot = new AtomicReference<>(null);

    @Value("${app.notifications.sales-change-threshold-percent:20.0}")
    private double salesChangeThresholdPercent;

    @Value("${app.notifications.lookback-days:30}")
    private int lookbackDays;

    /**
     * Monatlicher Check: 1. jeden Monats um 07:00.
     * Für lokale Tests kann der Cron-Ausdruck über die Umgebungsvariable
     * NOTIFICATION_CRON überschrieben werden.
     */
    @Scheduled(cron = "${app.notifications.cron:0 0 7 1 * *}")
    public void checkSalesAlerts() {
        log.info("Systembenachrichtigungs-Scheduler gestartet (Schwellenwert: {}%, Zeitraum: {} Tage)",
                salesChangeThresholdPercent, lookbackDays);

        List<SystemNotification> created = new ArrayList<>();
        created.addAll(checkZeroSales());
        created.addAll(checkSignificantSalesChange());

        if (!created.isEmpty()) {
            sendEmailDigest(created);
        }

        log.info("Systembenachrichtigungs-Scheduler abgeschlossen. {} neue Benachrichtigungen erstellt.",
                created.size());
    }

    /**
     * US #90 — Artikel mit exakt 0 Verkäufen im aktuellen Zeitraum.
     * Ausgeschlossen: nicht-kaufbare, lagerlose und im Zeitraum neu angelegte
     * Artikel.
     */
    private List<SystemNotification> checkZeroSales() {
        Instant periodStart = periodStart(lookbackDays);
        Instant now = Instant.now();
        Instant cutoffForNewProducts = periodStart;

        List<Product> candidates = purchasableCandidates(cutoffForNewProducts);
        List<SystemNotification> result = new ArrayList<>();

        for (Product product : candidates) {
            long units = sumUnits(product.getId(), periodStart, now);
            if (units == 0) {
                long prevUnits = sumUnits(product.getId(), periodStart(lookbackDays * 2), periodStart);
                SystemNotification n = notificationService.create(
                        SystemNotificationType.ZERO_SALES,
                        product.getId(),
                        product.getName(),
                        BigDecimal.valueOf(-100),
                        0,
                        prevUnits);
                result.add(n);
                log.warn("ALERT [US#90/ZERO_SALES]: \"{}\" (id={}) hatte 0 Verkäufe in den letzten {} Tagen",
                        product.getName(), product.getId(), lookbackDays);
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
        return result;
    }

    /**
     * US #90 — Artikel mit mehr als threshold % Veränderung (Anstieg oder
     * Rückgang).
     */
    private List<SystemNotification> checkSignificantSalesChange() {
        Instant currentStart = periodStart(lookbackDays);
        Instant previousStart = periodStart(lookbackDays * 2);
        Instant now = Instant.now();
        Instant cutoffForNewProducts = previousStart;

        List<Product> candidates = purchasableCandidates(cutoffForNewProducts);
        List<SystemNotification> result = new ArrayList<>();

        for (Product product : candidates) {
            long currentUnits = sumUnits(product.getId(), currentStart, now);
            long previousUnits = sumUnits(product.getId(), previousStart, currentStart);

            if (previousUnits == 0)
                continue;

            double changePercent = ((double) (currentUnits - previousUnits) / previousUnits) * 100.0;

            if (Math.abs(changePercent) > salesChangeThresholdPercent) {
                SystemNotificationType type = changePercent < 0
                        ? SystemNotificationType.SALES_DROP
                        : SystemNotificationType.SALES_INCREASE;

                BigDecimal changeDecimal = BigDecimal.valueOf(changePercent).setScale(2, RoundingMode.HALF_UP);
                SystemNotification n = notificationService.create(
                        type,
                        product.getId(),
                        product.getName(),
                        changeDecimal,
                        currentUnits,
                        previousUnits);
                result.add(n);
                log.warn("ALERT [US#90/{}]: \"{}\" (id={}) Änderung: {}% (vorher: {} Stk., jetzt: {} Stk.)",
                        type, product.getName(), product.getId(),
                        String.format("%.1f", changePercent), previousUnits, currentUnits);
            }
        }
        return result;
    }

    /** Alle kaufbaren, vorrätigen und vor dem cutoff angelegten Produkte. */
    private List<Product> purchasableCandidates(Instant createdBefore) {
        return productRepository.searchProducts(true, "", "")
                .stream()
                .filter(p -> p.getStock() > 0)
                .filter(p -> p.getCreatedAt().isBefore(createdBefore))
                .toList();
    }

    private long sumUnits(Long productId, Instant from, Instant to) {
        return orderRepository
                .findOrderItemsByProductIdAndDateRange(productId, from, to)
                .stream()
                .mapToLong(OrderItem::getQuantity)
                .sum();
    }

    private Instant periodStart(int days) {
        return LocalDate.now().minusDays(days).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Gebündelter E-Mail-Digest an alle aktiven Vertriebsmitarbeiter. */
    private void sendEmailDigest(List<SystemNotification> notifications) {
        List<User> recipients = userRepository.findByRoleAndActiveTrue(UserRole.SALES_EMPLOYEE);
        if (recipients.isEmpty()) {
            log.debug("Kein aktiver Vertriebsmitarbeiter gefunden, kein E-Mail-Digest versendet.");
            return;
        }

        String subject = String.format("Webshop Verkaufsmonitoring: %d neue Systemmeldung(en)", notifications.size());
        String body = buildEmailBody(notifications);

        for (User recipient : recipients) {
            emailService.sendEmail(recipient.getEmail(), subject, body);
        }
        log.info("E-Mail-Digest an {} Empfänger versendet.", recipients.size());
    }

    private String buildEmailBody(List<SystemNotification> notifications) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hallo,\n\n");
        sb.append("das automatische Verkaufsmonitoring hat folgende Auffälligkeiten festgestellt:\n\n");

        long drops = notifications.stream().filter(n -> n.getType() == SystemNotificationType.SALES_DROP).count();
        long rises = notifications.stream().filter(n -> n.getType() == SystemNotificationType.SALES_INCREASE).count();
        long zeroSales = notifications.stream().filter(n -> n.getType() == SystemNotificationType.ZERO_SALES).count();

        if (drops > 0)
            sb.append("  • ").append(drops).append(" Artikel mit Verkaufsrückgang (>")
                    .append((int) salesChangeThresholdPercent).append("%)\n");
        if (rises > 0)
            sb.append("  • ").append(rises).append(" Artikel mit Verkaufsanstieg (>")
                    .append((int) salesChangeThresholdPercent).append("%)\n");
        if (zeroSales > 0)
            sb.append("  • ").append(zeroSales).append(" Artikel ohne Verkäufe im Beobachtungszeitraum\n");

        sb.append("\nDetails:\n");
        sb.append("─".repeat(60)).append("\n");

        for (SystemNotification n : notifications) {
            sb.append(SystemNotificationResponse.from(n).message()).append("\n");
        }

        sb.append("\n─".repeat(60)).append("\n");
        sb.append("Die vollständige Liste finden Sie im Webshop-Dashboard unter „Systemmeldungen“.\n");
        sb.append("Klicken Sie auf eine Benachrichtigung, um direkt zur Artikelstatistik zu gelangen.\n\n");
        sb.append("Beobachtungszeitraum: letzte ").append(lookbackDays).append(" Tage\n");
        sb.append("Schwellenwert: ").append((int) salesChangeThresholdPercent).append("%\n\n");
        sb.append("Mit freundlichen Grüßen\nIhr Webshop-System");
        return sb.toString();
    }
}
