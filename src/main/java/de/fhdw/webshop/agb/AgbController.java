package de.fhdw.webshop.agb;

import de.fhdw.webshop.agb.dto.AgbVersionResponse;
import de.fhdw.webshop.agb.dto.CreateAgbVersionRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AgbController {

    private final AgbService agbService;

    @GetMapping("/api/agb/latest")
    public ResponseEntity<AgbVersionResponse> getLatest() {
        return ResponseEntity.ok(agbService.getLatestVersion());
    }

    @PostMapping("/api/admin/agb")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgbVersionResponse> createVersion(
            @Valid @RequestBody CreateAgbVersionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(agbService.createNewVersion(request.agbText(), currentUser));
    }
}
