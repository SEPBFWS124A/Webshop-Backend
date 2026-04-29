package de.fhdw.webshop.admin.statistics.alerting;

import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertThresholdRequest;
import de.fhdw.webshop.admin.statistics.alerting.dto.StatisticAlertWarningResponse;
import de.fhdw.webshop.order.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticAlertServiceTest {

    @Test
    void rejectsInvalidThresholdValuesBeforeSaving() {
        StatisticAlertThresholdRepository thresholdRepository = mock(StatisticAlertThresholdRepository.class);
        StatisticAlertWarningRepository warningRepository = mock(StatisticAlertWarningRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        StatisticAlertService service = new StatisticAlertService(thresholdRepository, warningRepository, entityManager);

        StatisticAlertThresholdRequest request = new StatisticAlertThresholdRequest(
                StatisticMetric.REVENUE,
                "Mein Umsatz",
                30,
                BigDecimal.ZERO,
                true
        );

        assertThatThrownBy(() -> service.createOrUpdateThreshold(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Der Schwellwert muss groesser als 0 und maximal 1000 Prozent sein.");
    }

    @Test
    void createsWarningWhenStoredThresholdIsExceeded() {
        StatisticAlertThresholdRepository thresholdRepository = mock(StatisticAlertThresholdRepository.class);
        StatisticAlertWarningRepository warningRepository = mock(StatisticAlertWarningRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query currentQuery = mock(Query.class);
        Query comparisonQuery = mock(Query.class);
        StatisticAlertService service = new StatisticAlertService(thresholdRepository, warningRepository, entityManager);
        StatisticAlertThreshold threshold = new StatisticAlertThreshold();
        threshold.setId(7L);
        threshold.setMetric(StatisticMetric.REVENUE);
        threshold.setMetricLabel("Umsatz kritisch");
        threshold.setCalculationMetric(StatisticMetric.REVENUE);
        threshold.setPeriodDays(30);
        threshold.setDeviationPercent(new BigDecimal("20.00"));
        threshold.setEnabled(true);

        when(thresholdRepository.findAllByEnabledTrueOrderByMetricLabelAsc()).thenReturn(List.of(threshold));
        when(warningRepository.existsByThresholdAndPeriodStartAndPeriodEnd(
                threshold,
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 29)
        )).thenReturn(false);
        when(entityManager.createQuery(anyString())).thenReturn(currentQuery, comparisonQuery);
        when(currentQuery.setParameter(anyString(), any())).thenReturn(currentQuery);
        when(comparisonQuery.setParameter(anyString(), any())).thenReturn(comparisonQuery);
        when(currentQuery.getSingleResult()).thenReturn(new BigDecimal("130.00"));
        when(comparisonQuery.getSingleResult()).thenReturn(new BigDecimal("100.00"));
        when(warningRepository.save(any(StatisticAlertWarning.class))).thenAnswer(invocation -> {
            StatisticAlertWarning warning = invocation.getArgument(0);
            warning.setId(42L);
            return warning;
        });

        List<StatisticAlertWarningResponse> warnings = service.evaluateEnabledThresholds(LocalDate.of(2026, 4, 30));

        assertThat(warnings).hasSize(1);
        StatisticAlertWarningResponse warning = warnings.getFirst();
        assertThat(warning.metric()).isEqualTo(StatisticMetric.REVENUE);
        assertThat(warning.metricLabel()).isEqualTo("Umsatz kritisch");
        assertThat(warning.periodStart()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(warning.periodEnd()).isEqualTo(LocalDate.of(2026, 4, 29));
        assertThat(warning.comparisonStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(warning.comparisonEnd()).isEqualTo(LocalDate.of(2026, 3, 30));
        assertThat(warning.deviationPercent()).isEqualByComparingTo("30.00");
        assertThat(warning.reason()).contains("Umsatz kritisch", "Schwellwert");
        assertThat(warning.status()).isEqualTo(StatisticAlertStatus.OPEN);
        verify(currentQuery).setParameter("cancelledStatus", OrderStatus.CANCELLED);
    }

    @Test
    void createsCustomMetricThresholdWithUniqueName() {
        StatisticAlertThresholdRepository thresholdRepository = mock(StatisticAlertThresholdRepository.class);
        StatisticAlertWarningRepository warningRepository = mock(StatisticAlertWarningRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        StatisticAlertService service = new StatisticAlertService(thresholdRepository, warningRepository, entityManager);
        StatisticAlertThresholdRequest request = new StatisticAlertThresholdRequest(
                StatisticMetric.AVG_ORDER_VALUE,
                "Premium-Warenkorbwert",
                14,
                new BigDecimal("12.50"),
                true
        );

        when(thresholdRepository.existsByMetricLabelIgnoreCase("Premium-Warenkorbwert")).thenReturn(false);
        when(thresholdRepository.save(any(StatisticAlertThreshold.class))).thenAnswer(invocation -> {
            StatisticAlertThreshold threshold = invocation.getArgument(0);
            threshold.setId(99L);
            return threshold;
        });

        var response = service.createOrUpdateThreshold(request);

        assertThat(response.metric()).isEqualTo(StatisticMetric.AVG_ORDER_VALUE);
        assertThat(response.metricLabel()).isEqualTo("Premium-Warenkorbwert");
        assertThat(response.periodDays()).isEqualTo(14);
        assertThat(response.deviationPercent()).isEqualByComparingTo("12.50");
    }
}
