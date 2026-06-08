package de.fhdw.webshop.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, NewsletterSubscriptionId> {
    @Query("SELECT ns FROM NewsletterSubscription ns WHERE ns.id.userId = :userId")
    List<NewsletterSubscription> findByUserId(@Param("userId") Long userId);
}
