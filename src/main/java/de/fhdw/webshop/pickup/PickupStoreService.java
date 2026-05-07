package de.fhdw.webshop.pickup;

import de.fhdw.webshop.pickup.dto.PickupStoreResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickupStoreService {

    private final PickupStoreRepository pickupStoreRepository;

    public List<PickupStoreResponse> listAvailableStores() {
        return pickupStoreRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    public PickupStoreResponse toResponse(PickupStore pickupStore) {
        if (pickupStore == null) {
            return null;
        }

        return new PickupStoreResponse(
                pickupStore.getId(),
                pickupStore.getName(),
                pickupStore.getStreet(),
                pickupStore.getPostalCode(),
                pickupStore.getCity(),
                pickupStore.getCountry(),
                pickupStore.getOpeningHours()
        );
    }
}
