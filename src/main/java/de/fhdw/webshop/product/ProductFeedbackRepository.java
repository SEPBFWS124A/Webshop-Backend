package de.fhdw.webshop.product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {
}
