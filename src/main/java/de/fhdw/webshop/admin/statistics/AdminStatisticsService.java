package de.fhdw.webshop.admin.statistics;

import de.fhdw.webshop.admin.statistics.dto.AdminProductPerformanceResponse;
import de.fhdw.webshop.admin.statistics.dto.AdminStatisticsDashboardResponse;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatisticsService {

    private static final int PRODUCT_PERFORMANCE_LIMIT = 8;

    private final EntityManager entityManager;
    private final UserRepository userRepository;

    public AdminStatisticsDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        LocalDate resolvedTo = to != null ? to : LocalDate.now();
        LocalDate resolvedFrom = from != null ? from : resolvedTo.minusDays(90);

        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("Das Von-Datum darf nicht nach dem Bis-Datum liegen.");
        }

        Instant fromInstant = resolvedFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = resolvedTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Object[] totals = (Object[]) entityManager.createQuery("""
                        SELECT SUM(o.totalPrice), COUNT(o), COUNT(DISTINCT o.customer.id)
                        FROM Order o
                        WHERE o.createdAt >= :from
                          AND o.createdAt < :to
                          AND o.status <> :cancelledStatus
                        """)
                .setParameter("from", fromInstant)
                .setParameter("to", toExclusive)
                .setParameter("cancelledStatus", OrderStatus.CANCELLED)
                .getSingleResult();

        BigDecimal revenue = toBigDecimal(totals[0]);
        long orderCount = toLong(totals[1]);
        long activeCustomerCount = userRepository.countActiveCustomers(UserRole.CUSTOMER);
        List<AdminProductPerformanceResponse> productPerformance = loadProductPerformance(fromInstant, toExclusive);

        return new AdminStatisticsDashboardResponse(
                resolvedFrom,
                resolvedTo,
                revenue,
                orderCount,
                activeCustomerCount,
                productPerformance,
                orderCount > 0
        );
    }

    private List<AdminProductPerformanceResponse> loadProductPerformance(Instant from, Instant toExclusive) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT
                            p.id,
                            p.name,
                            p.category,
                            p.purchasable,
                            COALESCE(SUM(CASE WHEN o.id IS NULL THEN 0 ELSE oi.quantity END), 0) AS units_sold,
                            COALESCE(SUM(CASE WHEN o.id IS NULL THEN 0 ELSE oi.price_at_order_time * oi.quantity END), 0) AS revenue
                        FROM products p
                        LEFT JOIN order_items oi ON oi.product_id = p.id
                        LEFT JOIN orders o ON o.id = oi.order_id
                            AND o.created_at >= :from
                            AND o.created_at < :to
                            AND o.status <> CAST(:cancelledStatus AS order_status)
                        GROUP BY p.id, p.name, p.category, p.purchasable
                        HAVING COALESCE(SUM(CASE WHEN o.id IS NULL THEN 0 ELSE oi.quantity END), 0) > 0
                        ORDER BY units_sold DESC, revenue DESC, p.name ASC
                        """)
                .setParameter("from", from)
                .setParameter("to", toExclusive)
                .setParameter("cancelledStatus", OrderStatus.CANCELLED.name())
                .setMaxResults(PRODUCT_PERFORMANCE_LIMIT)
                .getResultList();

        return rows.stream()
                .map(row -> new AdminProductPerformanceResponse(
                        toLong(row[0]),
                        String.valueOf(row[1]),
                        row[2] == null ? "Ohne Kategorie" : String.valueOf(row[2]),
                        Boolean.TRUE.equals(row[3]),
                        toLong(row[4]),
                        toBigDecimal(row[5])
                ))
                .toList();
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
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }
}
