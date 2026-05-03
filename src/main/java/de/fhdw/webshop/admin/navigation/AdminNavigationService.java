package de.fhdw.webshop.admin.navigation;

import de.fhdw.webshop.admin.navigation.dto.AdminNavigationGroupResponse;
import de.fhdw.webshop.admin.navigation.dto.AdminNavigationItemResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminNavigationService {

    private static final Set<UserRole> PRODUCT_MANAGEMENT_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> CUSTOMER_MANAGEMENT_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> SALES_REPORTING_ROLES =
            Set.of(UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> WAREHOUSE_ROLES =
            Set.of(UserRole.WAREHOUSE_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> BUSINESS_LOG_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> ADMIN_ROLES =
            Set.of(UserRole.ADMIN);
    private static final Set<UserRole> TRADE_IN_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> RETURN_REQUEST_ROLES =
            Set.of(UserRole.WAREHOUSE_EMPLOYEE, UserRole.ADMIN);

    private static final List<NavigationGroup> NAVIGATION = List.of(
            new NavigationGroup(
                    "user-management",
                    "Benutzerverwaltung",
                    "pi pi-id-card",
                    List.of(
                            item("admin-users", "Benutzer & Rollen", "/admin/users", "pi pi-users", ADMIN_ROLES)
                    )
            ),
            new NavigationGroup(
                    "customer-management",
                    "Kundenverwaltung",
                    "pi pi-address-book",
                    List.of(
                            item("customers", "Kunden & CRM", "/admin/customers", "pi pi-users", CUSTOMER_MANAGEMENT_ROLES),
                            item("customer-preview", "Kundenvorschau", "/admin/customers/preview", "pi pi-eye", CUSTOMER_MANAGEMENT_ROLES)
                    )
            ),
            new NavigationGroup(
                    "product-management",
                    "Produktverwaltung",
                    "pi pi-box",
                    List.of(
                            item("product-articles", "Artikel", "/admin/products/articles", "pi pi-list", PRODUCT_MANAGEMENT_ROLES),
                            item("product-categories", "Kategorien", "/admin/products/categories", "pi pi-tags", PRODUCT_MANAGEMENT_ROLES),
                            item("product-attributes", "Attribute", "/admin/products/attributes", "pi pi-sliders-h", PRODUCT_MANAGEMENT_ROLES),
                            item("product-pricing", "Preise & Aktionen", "/admin/products/pricing", "pi pi-percentage", SALES_REPORTING_ROLES),
                            item("warehouse", "Lager & Versand", "/admin/products/warehouse", "pi pi-truck", WAREHOUSE_ROLES)
                    )
            ),
            new NavigationGroup(
                    "orders-management",
                    "Bestellungen",
                    "pi pi-shopping-bag",
                    List.of(
                            item("return-requests", "Retouren", "/admin/orders/returns", "pi pi-barcode", RETURN_REQUEST_ROLES),
                            item("trade-in", "Trade-In Anfragen", "/admin/orders/trade-in", "pi pi-refresh", TRADE_IN_ROLES)
                    )
            ),
            new NavigationGroup(
                    "reports",
                    "Statistiken/Berichte",
                    "pi pi-chart-line",
                    List.of(
                            item("statistics-dashboard", "Dashboard", "/admin/reports/statistics", "pi pi-chart-line", SALES_REPORTING_ROLES),
                            item("statistics-alerts", "Kennzahl-Warnungen", "/admin/reports/statistics-alerts", "pi pi-exclamation-triangle", SALES_REPORTING_ROLES),
                            item("product-statistics", "Artikelstatistiken", "/admin/reports/product-statistics", "pi pi-chart-bar", SALES_REPORTING_ROLES),
                            item("monitoring", "System Monitoring", "/admin/reports/monitoring", "pi pi-desktop", ADMIN_ROLES)
                    )
            ),
            new NavigationGroup(
                    "system",
                    "System",
                    "pi pi-cog",
                    List.of(
                            item("business-log", "Business Log", "/admin/system/business-log", "pi pi-history", BUSINESS_LOG_ROLES),
                            item("notifications", "Systemmeldungen", "/admin/system/notifications", "pi pi-bell", SALES_REPORTING_ROLES),
                            item("alerting", "E-Mail & Alerting", "/admin/system/alerting", "pi pi-envelope", ADMIN_ROLES)
                    )
            )
    );

    private final UserRepository userRepository;

    public List<AdminNavigationGroupResponse> getNavigationFor(User currentUser) {
        User persistentUser = userRepository.findById(currentUser.getId())
                .filter(User::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Active user not found: " + currentUser.getId()));
        Set<UserRole> roles = persistentUser.getRoles();

        return NAVIGATION.stream()
                .map(group -> new AdminNavigationGroupResponse(
                        group.id(),
                        group.label(),
                        group.icon(),
                        group.items().stream()
                                .filter(item -> canSee(item, roles))
                                .map(item -> new AdminNavigationItemResponse(
                                        item.id(),
                                        item.label(),
                                        item.path(),
                                        item.icon(),
                                        item.allowedRoles()
                                ))
                                .toList()
                ))
                .filter(group -> !group.items().isEmpty())
                .toList();
    }

    private static NavigationItem item(String id, String label, String path, String icon, Set<UserRole> allowedRoles) {
        return new NavigationItem(id, label, path, icon, allowedRoles);
    }

    private static boolean canSee(NavigationItem item, Set<UserRole> userRoles) {
        return userRoles != null && item.allowedRoles().stream().anyMatch(userRoles::contains);
    }

    private record NavigationGroup(
            String id,
            String label,
            String icon,
            List<NavigationItem> items
    ) {}

    private record NavigationItem(
            String id,
            String label,
            String path,
            String icon,
            Set<UserRole> allowedRoles
    ) {}
}
