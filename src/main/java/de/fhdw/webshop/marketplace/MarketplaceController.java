package de.fhdw.webshop.marketplace;

import de.fhdw.webshop.marketplace.dto.MarketplaceProductDto;
import de.fhdw.webshop.marketplace.dto.MarketplaceSellerDto;
import de.fhdw.webshop.marketplace.dto.MarketplaceSellerProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/products")
    public ResponseEntity<List<MarketplaceProductDto>> listProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sellerName) {
        return ResponseEntity.ok(marketplaceService.listProducts(category, sellerName));
    }

    @GetMapping("/sellers")
    public ResponseEntity<List<MarketplaceSellerDto>> listSellers() {
        return ResponseEntity.ok(marketplaceService.listSellers());
    }

    @GetMapping("/sellers/{sellerName}")
    public ResponseEntity<MarketplaceSellerProfileDto> getSellerProfile(@PathVariable String sellerName) {
        return ResponseEntity.ok(marketplaceService.getSellerProfile(sellerName));
    }
}
