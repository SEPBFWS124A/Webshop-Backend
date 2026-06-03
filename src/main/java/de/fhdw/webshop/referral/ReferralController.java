package de.fhdw.webshop.referral;

import de.fhdw.webshop.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referral")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/my-code")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ReferralCodeResponse> getMyCode(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(referralService.getOrCreateCode(currentUser));
    }
}
