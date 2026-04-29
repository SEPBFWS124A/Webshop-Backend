package de.fhdw.webshop.admin;

import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/navigation")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')")
public class AdminNavigationController {

    private static final Set<UserRole> ADMIN_ROLES = Set.of(UserRole.ADMIN);
    private static final Set<UserRole> CUSTOMER_MANAGEMENT_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> PRODUCT_MANAGEMENT_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> SALES_REPORTING_ROLES =
            Set.of(UserRole.SALES_EMPLOYEE, UserRole.ADMIN);
    private static final Set<UserRole> WAREHOUSE_ROLES =
            Set.of(UserRole.WAREHOUSE_EMPLOYEE, UserRole.ADMIN);

    private static final List<AdminNavigationGroupResponse> NAVIGATION_GROUPS = List.of(
            new AdminNavigationGroupResponse(
                    "user-management",
                    "Benutzerverwaltung",
                    "pi pi-id-card",
                    List.of(
                            new AdminNavigationItemResponse(
                                    "admin-users",
                                    "Benutzer & Rollen",
                                    "/admin/users",
                                    "pi pi-users",
                                    ADMIN_ROLES,
                                    List.of("accounts", "mitarbeiter", "rollen")
                            )
                    )
            ),
            new AdminNavigationGroupResponse(
                    "customer-management",
                    "Kundenverwaltung",
                    "pi pi-address-book",
                    List.of(
                            new AdminNavigationItemResponse(
                                    "customers",
                                    "Kunden & CRM",
                                    "/admin/customers",
                                    "pi pi-users",
                                    CUSTOMER_MANAGEMENT_ROLES,
                                    List.of("kundenliste", "crm", "service")
                            ),
                            new AdminNavigationItemResponse(
                                    "customer-preview",
                                    "Kundenvorschau",
                                    "/admin/customers/preview",
                                    "pi pi-eye",
                                    CUSTOMER_MANAGEMENT_ROLES,
                                    List.of("kundensicht", "warenkorb")
                            )
                    )
            ),
            new AdminNavigationGroupResponse(
                    "product-management",
                    "Produktverwaltung",
                    "pi pi-box",
                    List.of(
                            new AdminNavigationItemResponse(
                                    "product-articles",
                                    "Artikel",
                                    "/admin/products/articles",
                                    "pi pi-list",
                                    PRODUCT_MANAGEMENT_ROLES,
                                    List.of("produkte", "sortiment", "katalog")
                            ),
                            new AdminNavigationItemResponse(
                                    "product-categories",
                                    "Kategorien",
                                    "/admin/products/categories",
                                    "pi pi-tags",
                                    PRODUCT_MANAGEMENT_ROLES,
                                    List.of("warengruppen", "katalog")
                            ),
                            new AdminNavigationItemResponse(
                                    "product-attributes",
                                    "Attribute",
                                    "/admin/products/attributes",
                                    "pi pi-sliders-h",
                                    PRODUCT_MANAGEMENT_ROLES,
                                    List.of("eigenschaften", "varianten")
                            ),
                            new AdminNavigationItemResponse(
                                    "product-pricing",
                                    "Preise & Aktionen",
                                    "/admin/products/pricing",
                                    "pi pi-percentage",
                                    SALES_REPORTING_ROLES,
                                    List.of("uvp", "rabatte", "promoted")
                            ),
                            new AdminNavigationItemResponse(
                                    "warehouse",
                                    "Lager & Versand",
                                    "/admin/products/warehouse",
                                    "pi pi-truck",
                                    WAREHOUSE_ROLES,
                                    List.of("bestellungen", "fulfillment", "pick pack")
                            )
                    )
            ),
            new AdminNavigationGroupResponse(
                    "reports",
                    "Statistiken/Berichte",
                    "pi pi-chart-line",
                    List.of(
                            new AdminNavigationItemResponse(
                                    "product-statistics",
                                    "Artikelstatistiken",
                                    "/admin/reports/product-statistics",
                                    "pi pi-chart-bar",
                                    SALES_REPORTING_ROLES,
                                    List.of("sales", "verkauf", "berichte")
                            ),
                            new AdminNavigationItemResponse(
                                    "monitoring",
                                    "System Monitoring",
                                    "/admin/reports/monitoring",
                                    "pi pi-desktop",
                                    ADMIN_ROLES,
                                    List.of("health", "grafana", "metriken")
                            )
                    )
            ),
            new AdminNavigationGroupResponse(
                    "system",
                    "System",
                    "pi pi-cog",
                    List.of(
                            new AdminNavigationItemResponse(
                                    "notifications",
                                    "Systemmeldungen",
                                    "/admin/system/notifications",
                                    "pi pi-bell",
                                    SALES_REPORTING_ROLES,
                                    List.of("benachrichtigungen", "alerts")
                            ),
                            new AdminNavigationItemResponse(
                                    "alerting",
                                    "E-Mail & Alerting",
                                    "/admin/system/alerting",
                                    "pi pi-envelope",
                                    ADMIN_ROLES,
                                    List.of("smtp", "email", "warnungen")
                            )
                    )
            )
    );

    @GetMapping
    public ResponseEntity<List<AdminNavigationGroupResponse>> getNavigation(@AuthenticationPrincipal User user) {
        Set<UserRole> userRoles = user.getRoles();

        List<AdminNavigationGroupResponse> visibleGroups = NAVIGATION_GROUPS.stream()
                .map(group -> new AdminNavigationGroupResponse(
                        group.id(),
                        group.label(),
                        group.icon(),
                        group.items().stream()
                                .filter(item -> hasAnyRole(userRoles, item.allowedRoles()))
                                .toList()
                ))
                .filter(group -> !group.items().isEmpty())
                .toList();

        return ResponseEntity.ok(visibleGroups);
    }

    private boolean hasAnyRole(Set<UserRole> userRoles, Set<UserRole> allowedRoles) {
        return allowedRoles.stream().anyMatch(userRoles::contains);
    }

    public record AdminNavigationGroupResponse(
            String id,
            String label,
            String icon,
            List<AdminNavigationItemResponse> items
    ) {
    }

    public record AdminNavigationItemResponse(
            String id,
            String label,
            String path,
            String icon,
            Set<UserRole> allowedRoles,
            List<String> keywords
    ) {
    }
}
