package de.fhdw.webshop.admin.navigation;

import de.fhdw.webshop.admin.navigation.dto.AdminNavigationGroupResponse;
import de.fhdw.webshop.admin.navigation.dto.AdminNavigationItemResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminNavigationServiceTest {

    @Test
    void loadsPersistentRolesBeforeBuildingNavigation() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminNavigationService service = new AdminNavigationService(userRepository);
        User principal = user(1L, UserRole.CUSTOMER);
        User persistentUser = user(1L, UserRole.ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(persistentUser));

        List<AdminNavigationGroupResponse> navigation = service.getNavigationFor(principal);

        verify(userRepository).findById(1L);
        assertThat(navigation)
                .extracting(AdminNavigationGroupResponse::label)
                .contains("Benutzerverwaltung", "Produktverwaltung", "Statistiken/Berichte");
    }

    @Test
    void onlyShowsWarehouseEntryForWarehouseEmployees() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminNavigationService service = new AdminNavigationService(userRepository);
        User warehouseUser = user(2L, UserRole.WAREHOUSE_EMPLOYEE);

        when(userRepository.findById(2L)).thenReturn(Optional.of(warehouseUser));

        List<AdminNavigationGroupResponse> navigation = service.getNavigationFor(warehouseUser);

        assertThat(navigation).extracting(AdminNavigationGroupResponse::label)
                .containsExactly("Produktverwaltung", "System");
        assertThat(navigation.getFirst().items())
                .extracting(AdminNavigationItemResponse::id)
                .containsExactly("warehouse");
    }

    @Test
    void hidesAdminOnlyItemsForSalesEmployees() {
        UserRepository userRepository = mock(UserRepository.class);
        AdminNavigationService service = new AdminNavigationService(userRepository);
        User salesUser = user(3L, UserRole.SALES_EMPLOYEE);

        when(userRepository.findById(3L)).thenReturn(Optional.of(salesUser));

        List<AdminNavigationGroupResponse> navigation = service.getNavigationFor(salesUser);
        List<String> itemIds = navigation.stream()
                .flatMap(group -> group.items().stream())
                .map(AdminNavigationItemResponse::id)
                .toList();

        assertThat(itemIds).contains("customers", "product-pricing", "statistics-dashboard", "product-statistics", "business-log", "notifications");
        assertThat(itemIds).doesNotContain(
                "admin-users",
                "admin-roles",
                "customer-promotions",
                "customer-revenue",
                "monitoring",
                "alerting",
                "warehouse");
    }

    private static User user(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setEmail("user-" + id + "@example.test");
        user.setPasswordHash("hash");
        user.getRoles().add(role);
        user.setActive(true);
        return user;
    }
}
