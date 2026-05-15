package de.fhdw.webshop.quote;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    boolean existsByQuoteNumber(String quoteNumber);

    List<QuoteRequest> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<QuoteRequest> findByIdAndCustomerId(Long id, Long customerId);
}
