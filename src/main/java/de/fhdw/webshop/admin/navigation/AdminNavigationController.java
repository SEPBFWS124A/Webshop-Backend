package de.fhdw.webshop.admin.navigation;

import de.fhdw.webshop.admin.navigation.dto.AdminNavigationGroupResponse;
import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/navigation")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'WAREHOUSE_EMPLOYEE', 'ADMIN')")
@RequiredArgsConstructor
public class AdminNavigationController {

    private final AdminNavigationService adminNavigationService;

    @GetMapping
    public ResponseEntity<List<AdminNavigationGroupResponse>> getNavigation(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(adminNavigationService.getNavigationFor(currentUser));
    }
}
