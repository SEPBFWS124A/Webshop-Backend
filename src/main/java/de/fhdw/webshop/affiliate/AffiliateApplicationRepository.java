package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AffiliateApplicationRepository extends JpaRepository<AffiliateApplication, Long> {

    Optional<AffiliateApplication> findByUser(User user);

    List<AffiliateApplication> findAllByOrderByCreatedAtDesc();

    List<AffiliateApplication> findByStatusOrderByCreatedAtDesc(AffiliateApplicationStatus status);
}
