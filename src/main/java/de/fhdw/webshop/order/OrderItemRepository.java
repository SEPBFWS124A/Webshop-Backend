package de.fhdw.webshop.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    Optional<OrderItem> findByIdAndOrderCustomerId(Long id, Long customerId);

    Optional<OrderItem> findByGiftCardCodeIgnoreCase(String giftCardCode);
}
