package de.fhdw.webshop.newsletter;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.newsletter.dto.*;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsletterService {

    private static final int MAX_IMAGES_PER_POST = 5;
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final NewsletterPostRepository postRepository;
    private final NewsletterPostImageRepository imageRepository;
    private final NewsletterCategoryRepository categoryRepository;
    private final NewsletterSubscriptionRepository subscriptionRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<NewsletterPostSummaryResponse> getPublishedPosts(String categorySlug) {
        List<NewsletterPost> posts = categorySlug != null && !categorySlug.isBlank()
                ? postRepository.findByCategorySlugAndPublishedTrueOrderByDisplayOrderAscPublishedAtDesc(categorySlug)
                : postRepository.findByPublishedTrueOrderByDisplayOrderAscPublishedAtDesc();
        return posts.stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public NewsletterPostResponse getPublishedPost(Long id) {
        NewsletterPost post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post nicht gefunden: " + id));
        if (!post.isPublished()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post nicht gefunden");
        }
        return toFullResponse(post);
    }

    @Transactional(readOnly = true)
    public List<NewsletterCategoryResponse> getCategories() {
        return categoryRepository.findAll().stream()
                .map(c -> new NewsletterCategoryResponse(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NewsletterCategoryStatsResponse> getCategoryStats() {
        List<Object[]> raw = categoryRepository.countSubscribersByCategory();
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : raw) {
            countMap.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findAll().stream()
                .map(c -> new NewsletterCategoryStatsResponse(
                        c.getId(), c.getName(), c.getSlug(),
                        countMap.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NewsletterPostSummaryResponse> getAllPostsForAdmin() {
        return postRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc().stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NewsletterPostResponse getPostForAdmin(Long id) {
        NewsletterPost post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post nicht gefunden: " + id));
        return toFullResponse(post);
    }

    @Transactional
    public NewsletterPostSummaryResponse createPost(CreateNewsletterPostRequest request,
                                                     List<MultipartFile> images,
                                                     User admin) {
        validateRequiredFields(request);
        validateImages(images);
        NewsletterCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategorie nicht gefunden"));

        NewsletterPost post = new NewsletterPost();
        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setCategory(category);
        post.setAuthor(admin);
        post.setScheduledPublishAt(request.scheduledPublishAt());

        NewsletterPost saved = postRepository.save(post);
        saveImages(saved, images);

        auditLogService.record(admin, "CREATE_NEWSLETTER_POST", "NewsletterPost", saved.getId(),
                AuditInitiator.ADMIN, "Newsletter-Post erstellt: " + saved.getTitle());
        return toSummaryResponse(saved);
    }

    @Transactional
    public NewsletterPostSummaryResponse updatePost(Long id, CreateNewsletterPostRequest request,
                                                     List<MultipartFile> images,
                                                     User admin) {
        NewsletterPost post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post nicht gefunden: " + id));
        validateRequiredFields(request);
        validateImages(images);

        NewsletterCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategorie nicht gefunden"));

        post.setTitle(request.title().trim());
        post.setContent(request.content().trim());
        post.setCategory(category);
        post.setScheduledPublishAt(request.scheduledPublishAt());

        NewsletterPost saved = postRepository.save(post);
        if (images != null && !images.isEmpty()) {
            saveImages(saved, images);
        }

        auditLogService.record(admin, "UPDATE_NEWSLETTER_POST", "NewsletterPost", saved.getId(),
                AuditInitiator.ADMIN, "Newsletter-Post aktualisiert: " + saved.getTitle());
        return toSummaryResponse(saved);
    }

    @Transactional
    public void deletePost(Long id, User admin) {
        NewsletterPost post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post nicht gefunden: " + id));
        String title = post.getTitle();
        postRepository.delete(post);
        auditLogService.record(admin, "DELETE_NEWSLETTER_POST", "NewsletterPost", id,
                AuditInitiator.ADMIN, "Newsletter-Post gelöscht: " + title);
    }

    @Transactional
    public NewsletterPostSummaryResponse togglePublish(Long id, User admin) {
        NewsletterPost post = postRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Post nicht gefunden: " + id));
        boolean nowPublished = !post.isPublished();
        post.setPublished(nowPublished);
        post.setPublishedAt(nowPublished ? Instant.now() : null);
        if (nowPublished) {
            post.setScheduledPublishAt(null);
        }
        NewsletterPost saved = postRepository.save(post);
        auditLogService.record(admin, nowPublished ? "PUBLISH_NEWSLETTER_POST" : "UNPUBLISH_NEWSLETTER_POST",
                "NewsletterPost", saved.getId(), AuditInitiator.ADMIN,
                "Newsletter-Post " + (nowPublished ? "veröffentlicht" : "zurückgezogen") + ": " + saved.getTitle());
        return toSummaryResponse(saved);
    }

    @Transactional
    public List<NewsletterPostSummaryResponse> reorderPosts(List<Long> orderedIds, User admin) {
        List<NewsletterPost> posts = postRepository.findAllByOrderByDisplayOrderAscCreatedAtDesc();
        for (int i = 0; i < orderedIds.size(); i++) {
            final int order = i;
            Long postId = orderedIds.get(i);
            posts.stream()
                    .filter(p -> p.getId().equals(postId))
                    .findFirst()
                    .ifPresent(p -> p.setDisplayOrder(order));
        }
        postRepository.saveAll(posts);
        auditLogService.record(admin, "REORDER_NEWSLETTER_POSTS", "NewsletterPost", null,
                AuditInitiator.ADMIN, "Newsletter-Posts neu angeordnet");
        return getAllPostsForAdmin();
    }

    @Transactional
    public void publishScheduledPosts() {
        List<NewsletterPost> due = postRepository.findDueForPublishing(Instant.now());
        for (NewsletterPost post : due) {
            post.setPublished(true);
            post.setPublishedAt(Instant.now());
            post.setScheduledPublishAt(null);
            postRepository.save(post);
        }
    }

    @Transactional
    public void deleteImage(Long imageId, User admin) {
        NewsletterPostImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Bild nicht gefunden: " + imageId));
        Long postId = image.getPost().getId();
        imageRepository.delete(image);
        auditLogService.record(admin, "DELETE_NEWSLETTER_IMAGE", "NewsletterPostImage", imageId,
                AuditInitiator.ADMIN, "Bild aus Newsletter-Post " + postId + " gelöscht");
    }

    @Transactional(readOnly = true)
    public List<NewsletterSubscriptionResponse> getSubscriptions(User user) {
        List<NewsletterSubscription> existing = subscriptionRepository.findByUserId(user.getId());
        List<NewsletterCategory> allCategories = categoryRepository.findAll();

        return allCategories.stream().map(cat -> {
            boolean subscribed = existing.stream()
                    .filter(s -> s.getCategory().getId().equals(cat.getId()))
                    .map(NewsletterSubscription::isSubscribed)
                    .findFirst()
                    .orElse(false);
            Instant updatedAt = existing.stream()
                    .filter(s -> s.getCategory().getId().equals(cat.getId()))
                    .map(NewsletterSubscription::getUpdatedAt)
                    .findFirst()
                    .orElse(null);
            return new NewsletterSubscriptionResponse(cat.getId(), cat.getName(), cat.getSlug(), subscribed, updatedAt);
        }).toList();
    }

    @Transactional
    public NewsletterSubscriptionResponse updateSubscription(User user, NewsletterSubscriptionRequest request) {
        NewsletterCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategorie nicht gefunden"));

        NewsletterSubscriptionId subscriptionId = new NewsletterSubscriptionId(user.getId(), category.getId());
        NewsletterSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseGet(() -> {
                    NewsletterSubscription s = new NewsletterSubscription();
                    s.setId(subscriptionId);
                    s.setUser(user);
                    s.setCategory(category);
                    return s;
                });
        subscription.setSubscribed(request.subscribed());
        NewsletterSubscription saved = subscriptionRepository.save(subscription);
        return new NewsletterSubscriptionResponse(
                category.getId(), category.getName(), category.getSlug(),
                saved.isSubscribed(), saved.getUpdatedAt());
    }

    private void validateRequiredFields(CreateNewsletterPostRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel darf nicht leer sein.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inhalt darf nicht leer sein.");
        }
        if (request.categoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kategorie ist erforderlich.");
        }
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;
        if (images.size() > MAX_IMAGES_PER_POST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Es können maximal " + MAX_IMAGES_PER_POST + " Bilder pro Post hochgeladen werden.");
        }
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Leere Bilddateien sind nicht erlaubt.");
            }
            if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein Bild darf maximal 5 MB groß sein.");
            }
            String contentType = image.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Nur Bilder im Format JPG, PNG oder WebP sind erlaubt.");
            }
        }
    }

    private void saveImages(NewsletterPost post, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;
        for (MultipartFile upload : images) {
            NewsletterPostImage image = new NewsletterPostImage();
            image.setPost(post);
            image.setOriginalFilename(upload.getOriginalFilename() != null ? upload.getOriginalFilename() : "bild");
            String contentType = upload.getContentType();
            image.setContentType(contentType != null ? contentType.toLowerCase() : "application/octet-stream");
            image.setFileSizeBytes(upload.getSize());
            try {
                image.setImageData(upload.getBytes());
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bild konnte nicht gelesen werden.");
            }
            imageRepository.save(image);
        }
    }

    private String toDataUrl(NewsletterPostImage image) {
        String encoded = Base64.getEncoder().encodeToString(image.getImageData());
        return "data:" + image.getContentType() + ";base64," + encoded;
    }

    private NewsletterImageResponse toImageResponse(NewsletterPostImage image) {
        return new NewsletterImageResponse(
                image.getId(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSizeBytes(),
                toDataUrl(image),
                image.getCreatedAt());
    }

    private NewsletterPostSummaryResponse toSummaryResponse(NewsletterPost post) {
        NewsletterCategory cat = post.getCategory();
        String previewImageDataUrl = imageRepository.findByPostIdOrderByCreatedAtAsc(post.getId())
                .stream().findFirst().map(this::toDataUrl).orElse(null);
        return new NewsletterPostSummaryResponse(
                post.getId(),
                post.getTitle(),
                new NewsletterCategoryResponse(cat.getId(), cat.getName(), cat.getSlug()),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.isPublished(),
                post.getPublishedAt(),
                post.getScheduledPublishAt(),
                post.getDisplayOrder(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                previewImageDataUrl);
    }

    private NewsletterPostResponse toFullResponse(NewsletterPost post) {
        NewsletterCategory cat = post.getCategory();
        List<NewsletterImageResponse> images = imageRepository.findByPostIdOrderByCreatedAtAsc(post.getId())
                .stream().map(this::toImageResponse).toList();
        return new NewsletterPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new NewsletterCategoryResponse(cat.getId(), cat.getName(), cat.getSlug()),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.isPublished(),
                post.getPublishedAt(),
                post.getScheduledPublishAt(),
                post.getDisplayOrder(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                images);
    }
}
