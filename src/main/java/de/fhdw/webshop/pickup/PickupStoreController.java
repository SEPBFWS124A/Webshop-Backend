package de.fhdw.webshop.pickup;

import de.fhdw.webshop.pickup.dto.PickupStoreResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pickup-stores")
@RequiredArgsConstructor
public class PickupStoreController {

    private final PickupStoreService pickupStoreService;

    @GetMapping
    public ResponseEntity<List<PickupStoreResponse>> listAvailableStores() {
        return ResponseEntity.ok(pickupStoreService.listAvailableStores());
    }
}
