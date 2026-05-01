package de.fhdw.webshop.tradein;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeInRepository extends JpaRepository<TradeInRequest, Long> {

    List<TradeInRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<TradeInRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByOrderItemIdAndStatusNot(Long orderItemId, TradeInStatus status);
}
