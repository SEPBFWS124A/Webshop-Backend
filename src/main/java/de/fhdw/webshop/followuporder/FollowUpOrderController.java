package de.fhdw.webshop.followuporder;

import de.fhdw.webshop.followuporder.dto.CreateFollowUpOrderRequest;
import de.fhdw.webshop.followuporder.dto.FollowUpOrderResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/follow-up-orders")
@RequiredArgsConstructor
public class FollowUpOrderController {

    private final FollowUpOrderService followUpOrderService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<FollowUpOrderResponse>> list(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(followUpOrderService.listForCustomer(currentUser.getId()));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<FollowUpOrderResponse> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateFollowUpOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(followUpOrderService.create(currentUser, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        followUpOrderService.cancel(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
