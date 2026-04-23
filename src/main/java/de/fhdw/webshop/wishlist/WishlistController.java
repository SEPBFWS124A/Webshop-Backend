package de.fhdw.webshop.wishlist;

import de.fhdw.webshop.user.User;
import de.fhdw.webshop.wishlist.dto.WishlistStateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/wishlist")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<WishlistStateDto> getWishlist(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(wishlistService.getWishlistState(currentUser));
    }

    @PutMapping
    public ResponseEntity<WishlistStateDto> saveWishlist(@AuthenticationPrincipal User currentUser,
                                                         @RequestBody WishlistStateDto wishlistState) {
        return ResponseEntity.ok(wishlistService.saveWishlistState(currentUser, wishlistState));
    }
}
