package de.fhdw.webshop.returnrequest;

import de.fhdw.webshop.returnrequest.dto.CreateReturnRequest;
import de.fhdw.webshop.returnrequest.dto.ReturnRequestResponse;
import de.fhdw.webshop.user.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    @PostMapping("/api/returns")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReturnRequestResponse> createReturnRequest(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(returnRequestService.createReturnRequest(currentUser, request));
    }

    @GetMapping("/api/returns/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<ReturnRequestResponse>> listMyReturnRequests(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(returnRequestService.listForCustomer(currentUser.getId()));
    }
}
