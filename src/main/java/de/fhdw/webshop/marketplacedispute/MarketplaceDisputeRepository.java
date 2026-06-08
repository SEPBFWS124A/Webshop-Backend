package de.fhdw.webshop.marketplacedispute;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceDisputeRepository extends JpaRepository<MarketplaceDispute, Long> {

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    List<MarketplaceDispute> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    List<MarketplaceDispute> findByOrderIdAndCustomerIdOrderByCreatedAtDesc(Long orderId, Long customerId);

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    List<MarketplaceDispute> findBySellerNameIgnoreCaseOrderByCreatedAtDesc(String sellerName);

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    List<MarketplaceDispute> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    Optional<MarketplaceDispute> findByIdAndCustomerId(Long id, Long customerId);

    @EntityGraph(attributePaths = {"order", "orderItem", "orderItem.product", "customer"})
    Optional<MarketplaceDispute> findByIdAndSellerNameIgnoreCase(Long id, String sellerName);

    boolean existsByOrderItemIdAndStatusIn(Long orderItemId, Collection<MarketplaceDisputeStatus> statuses);

    List<MarketplaceDispute> findBySellerNameIgnoreCaseAndStatusIn(String sellerName, Collection<MarketplaceDisputeStatus> statuses);
}
