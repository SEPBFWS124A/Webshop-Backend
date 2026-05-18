package de.fhdw.webshop.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomerIdOrderByUpdatedAtDesc(Long customerId);

    List<SupportTicket> findAllByOrderByUpdatedAtDesc();

    Optional<SupportTicket> findByIdAndCustomerId(Long id, Long customerId);
}
