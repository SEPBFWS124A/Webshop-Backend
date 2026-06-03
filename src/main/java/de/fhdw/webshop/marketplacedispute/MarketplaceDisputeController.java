package de.fhdw.webshop.marketplacedispute;

import de.fhdw.webshop.marketplacedispute.dto.CreateMarketplaceDisputeRequest;
import de.fhdw.webshop.marketplacedispute.dto.MarketplaceDisputeResponse;
import de.fhdw.webshop.marketplacedispute.dto.MarketplaceDisputeStatusUpdateRequest;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketplaceDisputeController {

    private final MarketplaceDisputeService disputeService;

    @PostMapping("/api/orders/{orderId}/items/{orderItemId}/disputes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<MarketplaceDisputeResponse> createDispute(
            @AuthenticationPrincipal User customer,
            @PathVariable Long orderId,
            @PathVariable Long orderItemId,
            @Valid @RequestBody CreateMarketplaceDisputeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disputeService.createDispute(customer, orderId, orderItemId, request));
    }

    @GetMapping("/api/customers/me/disputes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<MarketplaceDisputeResponse>> listMyDisputes(@AuthenticationPrincipal User customer) {
        return ResponseEntity.ok(disputeService.listCustomerDisputes(customer));
    }

    @GetMapping("/api/orders/{orderId}/disputes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<MarketplaceDisputeResponse>> listOrderDisputes(
            @AuthenticationPrincipal User customer,
            @PathVariable Long orderId
    ) {
        return ResponseEntity.ok(disputeService.listCustomerOrderDisputes(customer, orderId));
    }

    @GetMapping("/api/seller-portal/disputes")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<MarketplaceDisputeResponse>> listSellerDisputes(@AuthenticationPrincipal User seller) {
        return ResponseEntity.ok(disputeService.listSellerDisputes(seller));
    }

    @GetMapping("/api/seller-portal/disputes/{disputeId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<MarketplaceDisputeResponse> getSellerDispute(
            @AuthenticationPrincipal User seller,
            @PathVariable Long disputeId
    ) {
        return ResponseEntity.ok(disputeService.getSellerDispute(seller, disputeId));
    }

    @GetMapping("/api/admin/disputes")
    @PreAuthorize("hasAnyRole('EMPLOYEE','SALES_EMPLOYEE','ADMIN')")
    public ResponseEntity<List<MarketplaceDisputeResponse>> listAdminDisputes(
            @RequestParam(required = false) MarketplaceDisputeStatus status
    ) {
        return ResponseEntity.ok(disputeService.listAdminDisputes(status));
    }

    @PatchMapping("/api/admin/disputes/{disputeId}/status")
    @PreAuthorize("hasAnyRole('EMPLOYEE','SALES_EMPLOYEE','ADMIN')")
    public ResponseEntity<MarketplaceDisputeResponse> updateStatus(
            @AuthenticationPrincipal User employee,
            @PathVariable Long disputeId,
            @Valid @RequestBody MarketplaceDisputeStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(disputeService.updateStatus(employee, disputeId, request));
    }
}
