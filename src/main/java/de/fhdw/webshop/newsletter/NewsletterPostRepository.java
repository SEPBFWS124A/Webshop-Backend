package de.fhdw.webshop.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NewsletterPostRepository extends JpaRepository<NewsletterPost, Long> {
    List<NewsletterPost> findByPublishedTrueOrderByDisplayOrderAscPublishedAtDesc();
    List<NewsletterPost> findByCategorySlugAndPublishedTrueOrderByDisplayOrderAscPublishedAtDesc(String categorySlug);
    List<NewsletterPost> findAllByOrderByDisplayOrderAscCreatedAtDesc();

    @Query("SELECT p FROM NewsletterPost p WHERE p.published = false AND p.scheduledPublishAt IS NOT NULL AND p.scheduledPublishAt <= :now")
    List<NewsletterPost> findDueForPublishing(@Param("now") Instant now);
}
