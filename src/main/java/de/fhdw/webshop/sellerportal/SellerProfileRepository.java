package de.fhdw.webshop.sellerportal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<SellerProfile> findByUserId(Long userId);

    @EntityGraph(attributePaths = "user")
    List<SellerProfile> findAllByActiveTrueOrderByDisplayNameAsc();

    Optional<SellerProfile> findByDisplayNameIgnoreCase(String displayName);
}
