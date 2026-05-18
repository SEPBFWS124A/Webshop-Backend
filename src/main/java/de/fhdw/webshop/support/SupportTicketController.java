package de.fhdw.webshop.support;

import de.fhdw.webshop.support.dto.CreateSupportTicketMessageRequest;
import de.fhdw.webshop.support.dto.CreateSupportTicketRequest;
import de.fhdw.webshop.support.dto.RecentSupportOrderResponse;
import de.fhdw.webshop.support.dto.SupportTicketResponse;
import de.fhdw.webshop.support.dto.UpdateSupportTicketStatusRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/support-tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<SupportTicketResponse>> listMine(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(supportTicketService.listMine(currentUser));
    }

    @GetMapping("/mine/recent-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<RecentSupportOrderResponse>> recentOrders(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(supportTicketService.listRecentOrders(currentUser));
    }

    @PostMapping("/mine")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SupportTicketResponse> createMine(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateSupportTicketRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportTicketService.createMine(currentUser, request));
    }

    @GetMapping("/mine/{ticketId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SupportTicketResponse> getMine(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId
    ) {
        return ResponseEntity.ok(supportTicketService.getMine(ticketId, currentUser));
    }

    @PostMapping("/mine/{ticketId}/messages")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<SupportTicketResponse> addCustomerMessage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateSupportTicketMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportTicketService.addCustomerMessage(ticketId, currentUser, request));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<List<SupportTicketResponse>> listAdmin() {
        return ResponseEntity.ok(supportTicketService.listAdmin());
    }

    @GetMapping("/admin/{ticketId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SupportTicketResponse> getAdmin(@PathVariable Long ticketId) {
        return ResponseEntity.ok(supportTicketService.getAdmin(ticketId));
    }

    @PostMapping("/admin/{ticketId}/messages")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SupportTicketResponse> addAdminMessage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateSupportTicketMessageRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportTicketService.addAdminMessage(ticketId, currentUser, request));
    }

    @PatchMapping("/admin/{ticketId}/status")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SALES_EMPLOYEE', 'ADMIN')")
    public ResponseEntity<SupportTicketResponse> updateStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateSupportTicketStatusRequest request
    ) {
        return ResponseEntity.ok(supportTicketService.updateStatus(ticketId, currentUser, request));
    }
}
