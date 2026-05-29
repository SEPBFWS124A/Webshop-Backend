package de.fhdw.webshop.sellerportal;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerPayoutItemRepository extends JpaRepository<SellerPayoutItem, Long> {

    @EntityGraph(attributePaths = {"order", "returnRequest"})
    List<SellerPayoutItem> findByPayoutIdOrderByOccurredAtAscIdAsc(Long payoutId);

    void deleteByPayoutId(Long payoutId);
}
