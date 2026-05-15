package de.fhdw.webshop.discount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VolumeDiscountTierRepository extends JpaRepository<VolumeDiscountTier, Long> {

    List<VolumeDiscountTier> findAllByOrderByActiveDescDiscountPercentDescIdDesc();

    List<VolumeDiscountTier> findByActiveTrueOrderByDiscountPercentDescIdDesc();
}
