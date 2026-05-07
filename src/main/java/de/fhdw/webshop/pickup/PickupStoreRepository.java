package de.fhdw.webshop.pickup;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupStoreRepository extends JpaRepository<PickupStore, Long> {

    List<PickupStore> findByActiveTrueOrderByNameAsc();

    Optional<PickupStore> findByIdAndActiveTrue(Long id);
}
