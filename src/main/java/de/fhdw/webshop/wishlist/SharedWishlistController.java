package de.fhdw.webshop.wishlist;

import de.fhdw.webshop.wishlist.dto.SharedWishlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wishlist/shared")
@RequiredArgsConstructor
public class SharedWishlistController {

    private final WishlistService wishlistService;

    @GetMapping("/{shareToken}")
    public ResponseEntity<SharedWishlistResponse> getSharedWishlist(@PathVariable String shareToken) {
        return ResponseEntity.ok(wishlistService.getSharedWishlist(shareToken));
    }
}
