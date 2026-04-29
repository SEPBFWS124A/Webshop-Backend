package de.fhdw.webshop.admin.statistics;

import de.fhdw.webshop.admin.statistics.dto.AdminStatisticsDashboardResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminStatisticsServiceTest {

    @Test
    void usesDateRangeForAllDashboardKpis() {
        EntityManager entityManager = mock(EntityManager.class);
        Query totalsQuery = mock(Query.class);
        Query productPerformanceQuery = mock(Query.class);
        AdminStatisticsService service = new AdminStatisticsService(entityManager);

        when(entityManager.createQuery(anyString())).thenReturn(totalsQuery);
        when(entityManager.createNativeQuery(anyString())).thenReturn(productPerformanceQuery);
        when(totalsQuery.setParameter(anyString(), any())).thenReturn(totalsQuery);
        when(productPerformanceQuery.setParameter(anyString(), any())).thenReturn(productPerformanceQuery);
        when(productPerformanceQuery.setMaxResults(anyInt())).thenReturn(productPerformanceQuery);
        when(totalsQuery.getSingleResult()).thenReturn(new Object[] { new BigDecimal("119.90"), 3L, 2L });
        when(productPerformanceQuery.getResultList()).thenReturn(List.of());

        AdminStatisticsDashboardResponse dashboard = service.getDashboard(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30)
        );

        assertThat(dashboard.from()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(dashboard.revenue()).isEqualByComparingTo("119.90");
        assertThat(dashboard.orderCount()).isEqualTo(3);
        assertThat(dashboard.activeCustomerCount()).isEqualTo(2);
        assertThat(dashboard.hasOrderData()).isTrue();
        verify(totalsQuery).setParameter("cancelledStatus", OrderStatus.CANCELLED);
    }

    @Test
    void rejectsInvalidDateRangesBeforeQueryingDatabase() {
        EntityManager entityManager = mock(EntityManager.class);
        AdminStatisticsService service = new AdminStatisticsService(entityManager);

        assertThatThrownBy(() -> service.getDashboard(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 4, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Das Von-Datum darf nicht nach dem Bis-Datum liegen.");
    }
}
