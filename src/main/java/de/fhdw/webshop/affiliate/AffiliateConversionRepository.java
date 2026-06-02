package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AffiliateConversionRepository extends JpaRepository<AffiliateConversion, Long> {

    List<AffiliateConversion> findByAffiliateLinkOrderByCreatedAtDesc(AffiliateLink link);

    List<AffiliateConversion> findByAffiliateLink_AffiliateProfile_UserOrderByCreatedAtDesc(User user);

    List<AffiliateConversion> findByAffiliateLink_AffiliateProfileOrderByCreatedAtDesc(AffiliateProfile profile);

    boolean existsByAffiliateLinkAndOrder(AffiliateLink link, Order order);
}
