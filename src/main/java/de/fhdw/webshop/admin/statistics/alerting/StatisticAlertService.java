package de.fhdw.webshop.admin.statistics.alerting;

import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertThresholdRequest;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertThresholdResponse;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertWarningResponse;
import de.fhdw.webshop.order.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticAlertService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MAX_THRESHOLD_PERCENT = new BigDecimal("1000.00");

    private final StatisticAlertThresholdRepository thresholdRepository;
    private final StatisticAlertWarningRepository warningRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<StatisticAlertThresholdResponse> getThresholds() {
        return thresholdRepository.findAllByOrderByMetricAsc().stream()
                .map(StatisticAlertThresholdResponse::from)
                .toList();
    }

    @Transactional
    public StatisticAlertThresholdResponse createOrUpdateThreshold(StatisticAlertThresholdRequest request) {
        validateThresholdRequest(request);
        StatisticAlertThreshold threshold = thresholdRepository.findByMetric(request.metric())
                .orElseGet(StatisticAlertThreshold::new);
        applyRequest(threshold, request);
        return StatisticAlertThresholdResponse.from(thresholdRepository.save(threshold));
    }

    @Transactional
    public StatisticAlertThresholdResponse updateThreshold(Long id, StatisticAlertThresholdRequest request) {
        validateThresholdRequest(request);
        StatisticAlertThreshold threshold = thresholdRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Schwellwert nicht gefunden: " + id));
        applyRequest(threshold, request);
        return StatisticAlertThresholdResponse.from(thresholdRepository.save(threshold));
    }

    @Transactional
    public List<StatisticAlertWarningResponse> evaluateAndListWarnings(boolean includeDone) {
        evaluateEnabledThresholds(LocalDate.now(ZoneOffset.UTC));
        return listWarnings(includeDone);
    }

    @Transactional
    public List<StatisticAlertWarningResponse> evaluateEnabledThresholds(LocalDate today) {
        LocalDate periodEnd = today.minusDays(1);
        return thresholdRepository.findAllByEnabledTrueOrderByMetricAsc().stream()
                .map(threshold -> evaluateThreshold(threshold, periodEnd))
                .flatMap(List::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatisticAlertWarningResponse> listWarnings(boolean includeDone) {
        List<StatisticAlertWarning> warnings = includeDone
                ? warningRepository.findAllByOrderByCreatedAtDesc()
                : warningRepository.findAllByStatusNotOrderByCreatedAtDesc(StatisticAlertStatus.DONE);
        return warnings.stream()
                .map(StatisticAlertWarningResponse::from)
                .toList();
    }

    @Transactional
    public StatisticAlertWarningResponse markAsRead(Long warningId) {
        StatisticAlertWarning warning = getWarning(warningId);
        if (warning.getStatus() == StatisticAlertStatus.OPEN) {
            warning.setStatus(StatisticAlertStatus.READ);
            warning.setReadAt(Instant.now());
        }
        return StatisticAlertWarningResponse.from(warning);
    }

    @Transactional
    public StatisticAlertWarningResponse markAsDone(Long warningId) {
        StatisticAlertWarning warning = getWarning(warningId);
        Instant now = Instant.now();
        warning.setStatus(StatisticAlertStatus.DONE);
        if (warning.getReadAt() == null) {
            warning.setReadAt(now);
        }
        warning.setResolvedAt(now);
        return StatisticAlertWarningResponse.from(warning);
    }

    private List<StatisticAlertWarningResponse> evaluateThreshold(
            StatisticAlertThreshold threshold,
            LocalDate periodEnd
    ) {
        LocalDate periodStart = periodEnd.minusDays(threshold.getPeriodDays() - 1L);
        LocalDate comparisonEnd = periodStart.minusDays(1);
        LocalDate comparisonStart = comparisonEnd.minusDays(threshold.getPeriodDays() - 1L);

        if (warningRepository.existsByThresholdAndPeriodStartAndPeriodEnd(threshold, periodStart, periodEnd)) {
            return List.of();
        }

        BigDecimal currentValue = loadMetricValue(threshold.getMetric(), periodStart, periodEnd);
        BigDecimal comparisonValue = loadMetricValue(threshold.getMetric(), comparisonStart, comparisonEnd);
        BigDecimal deviationPercent = calculateDeviationPercent(currentValue, comparisonValue);

        if (deviationPercent.abs().compareTo(threshold.getDeviationPercent()) < 0) {
            return List.of();
        }

        StatisticAlertWarning warning = new StatisticAlertWarning();
        warning.setThreshold(threshold);
        warning.setMetric(threshold.getMetric());
        warning.setPeriodStart(periodStart);
        warning.setPeriodEnd(periodEnd);
        warning.setComparisonStart(comparisonStart);
        warning.setComparisonEnd(comparisonEnd);
        warning.setCurrentValue(currentValue);
        warning.setComparisonValue(comparisonValue);
        warning.setDeviationPercent(deviationPercent);
        warning.setReason(buildReason(threshold, deviationPercent));

        return List.of(StatisticAlertWarningResponse.from(warningRepository.save(warning)));
    }

    private BigDecimal loadMetricValue(StatisticMetric metric, LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return switch (metric) {
            case REVENUE -> toBigDecimal(entityManager.createQuery("""
                            SELECT SUM(o.totalPrice)
                            FROM Order o
                            WHERE o.createdAt >= :from
                              AND o.createdAt < :to
                              AND o.status <> :cancelledStatus
                            """)
                    .setParameter("from", from)
                    .setParameter("to", toExclusive)
                    .setParameter("cancelledStatus", OrderStatus.CANCELLED)
                    .getSingleResult());
            case ORDER_COUNT -> BigDecimal.valueOf(toLong(entityManager.createQuery("""
                            SELECT COUNT(o)
                            FROM Order o
                            WHERE o.createdAt >= :from
                              AND o.createdAt < :to
                              AND o.status <> :cancelledStatus
                            """)
                    .setParameter("from", from)
                    .setParameter("to", toExclusive)
                    .setParameter("cancelledStatus", OrderStatus.CANCELLED)
                    .getSingleResult()));
            case ACTIVE_CUSTOMERS -> BigDecimal.valueOf(toLong(entityManager.createQuery("""
                            SELECT COUNT(DISTINCT o.customer.id)
                            FROM Order o
                            WHERE o.createdAt >= :from
                              AND o.createdAt < :to
                              AND o.status <> :cancelledStatus
                            """)
                    .setParameter("from", from)
                    .setParameter("to", toExclusive)
                    .setParameter("cancelledStatus", OrderStatus.CANCELLED)
                    .getSingleResult()));
            case AVG_ORDER_VALUE -> toBigDecimal(entityManager.createQuery("""
                            SELECT AVG(o.totalPrice)
                            FROM Order o
                            WHERE o.createdAt >= :from
                              AND o.createdAt < :to
                              AND o.status <> :cancelledStatus
                            """)
                    .setParameter("from", from)
                    .setParameter("to", toExclusive)
                    .setParameter("cancelledStatus", OrderStatus.CANCELLED)
                    .getSingleResult());
        };
    }

    private static BigDecimal calculateDeviationPercent(BigDecimal currentValue, BigDecimal comparisonValue) {
        if (comparisonValue.compareTo(BigDecimal.ZERO) == 0) {
            if (currentValue.compareTo(BigDecimal.ZERO) == 0) {
                return ZERO;
            }
            return ONE_HUNDRED;
        }

        return currentValue.subtract(comparisonValue)
                .divide(comparisonValue.abs(), 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static String buildReason(StatisticAlertThreshold threshold, BigDecimal deviationPercent) {
        String direction = deviationPercent.signum() >= 0 ? "ueber" : "unter";
        return "%s liegt um %s%% %s dem Vergleichszeitraum und ueberschreitet den Schwellwert von %s%%."
                .formatted(
                        threshold.getMetric().getLabel(),
                        deviationPercent.abs().setScale(2, RoundingMode.HALF_UP),
                        direction,
                        threshold.getDeviationPercent().setScale(2, RoundingMode.HALF_UP)
                );
    }

    private static void validateThresholdRequest(StatisticAlertThresholdRequest request) {
        if (request.metric() == null) {
            throw new IllegalArgumentException("Eine Kennzahl muss ausgewaehlt werden.");
        }
        if (request.periodDays() == null || request.periodDays() < 1 || request.periodDays() > 365) {
            throw new IllegalArgumentException("Der Zeitraum muss zwischen 1 und 365 Tagen liegen.");
        }
        if (request.deviationPercent() == null
                || request.deviationPercent().compareTo(BigDecimal.ZERO) <= 0
                || request.deviationPercent().compareTo(MAX_THRESHOLD_PERCENT) > 0) {
            throw new IllegalArgumentException("Der Schwellwert muss groesser als 0 und maximal 1000 Prozent sein.");
        }
    }

    private static void applyRequest(StatisticAlertThreshold threshold, StatisticAlertThresholdRequest request) {
        threshold.setMetric(request.metric());
        threshold.setPeriodDays(request.periodDays());
        threshold.setDeviationPercent(request.deviationPercent().setScale(2, RoundingMode.HALF_UP));
        threshold.setEnabled(request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
        threshold.setUpdatedAt(Instant.now());
    }

    private StatisticAlertWarning getWarning(Long warningId) {
        return warningRepository.findById(warningId)
                .orElseThrow(() -> new EntityNotFoundException("Statistik-Warnung nicht gefunden: " + warningId));
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }
}
