package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AffiliateProfileRepository extends JpaRepository<AffiliateProfile, Long> {

    Optional<AffiliateProfile> findByUser(User user);
}
