package de.fhdw.webshop.newsletter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsletterPostImageRepository extends JpaRepository<NewsletterPostImage, Long> {
    List<NewsletterPostImage> findByPostIdOrderByCreatedAtAsc(Long postId);
}
