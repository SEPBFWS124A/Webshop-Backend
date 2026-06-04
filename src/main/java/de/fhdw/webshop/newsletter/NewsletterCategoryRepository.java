package de.fhdw.webshop.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NewsletterCategoryRepository extends JpaRepository<NewsletterCategory, Long> {
    Optional<NewsletterCategory> findBySlug(String slug);

    @Query("SELECT ns.category.id, COUNT(ns) FROM NewsletterSubscription ns WHERE ns.subscribed = true GROUP BY ns.category.id")
    List<Object[]> countSubscribersByCategory();
}
