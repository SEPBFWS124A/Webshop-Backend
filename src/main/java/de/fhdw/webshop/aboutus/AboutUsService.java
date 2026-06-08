package de.fhdw.webshop.aboutus;

import de.fhdw.webshop.aboutus.dto.*;
import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AboutUsService {

    private static final int MAX_IMAGES_PER_SECTION = 3;
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final AboutUsSectionRepository sectionRepository;
    private final AboutUsImageRepository imageRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AboutUsSectionResponse> getAllSections() {
        return sectionRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AboutUsSectionResponse createSection(CreateAboutUsSectionRequest request,
                                                 List<MultipartFile> images,
                                                 User admin) {
        validateRequiredFields(request);
        validateImages(images);
        AboutUsSection section = new AboutUsSection();
        section.setTitle(request.title().trim());
        section.setContent(request.content().trim());
        section.setDisplayOrder(request.displayOrder());
        section.setLayoutType(resolveLayoutType(request.layoutType()));

        AboutUsSection saved = sectionRepository.save(section);
        saveImages(saved, images);

        auditLogService.record(admin, "CREATE_ABOUT_US_SECTION", "AboutUsSection", saved.getId(),
                AuditInitiator.ADMIN, "Über-uns-Sektion erstellt: " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public AboutUsSectionResponse updateSection(Long id, CreateAboutUsSectionRequest request,
                                                 List<MultipartFile> images,
                                                 User admin) {
        AboutUsSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sektion nicht gefunden: " + id));
        validateRequiredFields(request);
        validateImages(images);

        section.setTitle(request.title().trim());
        section.setContent(request.content().trim());
        section.setDisplayOrder(request.displayOrder());
        section.setLayoutType(resolveLayoutType(request.layoutType()));

        AboutUsSection saved = sectionRepository.save(section);
        if (images != null && !images.isEmpty()) {
            saveImages(saved, images);
        }

        auditLogService.record(admin, "UPDATE_ABOUT_US_SECTION", "AboutUsSection", saved.getId(),
                AuditInitiator.ADMIN, "Über-uns-Sektion aktualisiert: " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public void deleteSection(Long id, User admin) {
        AboutUsSection section = sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sektion nicht gefunden: " + id));
        String title = section.getTitle();
        sectionRepository.delete(section);
        auditLogService.record(admin, "DELETE_ABOUT_US_SECTION", "AboutUsSection", id,
                AuditInitiator.ADMIN, "Über-uns-Sektion gelöscht: " + title);
    }

    @Transactional
    public List<AboutUsSectionResponse> reorderSections(List<Long> orderedIds, User admin) {
        List<AboutUsSection> sections = sectionRepository.findAllByOrderByDisplayOrderAsc();
        for (int i = 0; i < orderedIds.size(); i++) {
            final int order = i;
            Long sectionId = orderedIds.get(i);
            sections.stream()
                    .filter(s -> s.getId().equals(sectionId))
                    .findFirst()
                    .ifPresent(s -> s.setDisplayOrder(order));
        }
        sectionRepository.saveAll(sections);
        auditLogService.record(admin, "REORDER_ABOUT_US_SECTIONS", "AboutUsSection", null,
                AuditInitiator.ADMIN, "Über-uns-Sektionen neu angeordnet");
        return getAllSections();
    }

    @Transactional
    public void deleteImage(Long imageId, User admin) {
        AboutUsImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new EntityNotFoundException("Bild nicht gefunden: " + imageId));
        Long sectionId = image.getSection().getId();
        imageRepository.delete(image);
        auditLogService.record(admin, "DELETE_ABOUT_US_IMAGE", "AboutUsImage", imageId,
                AuditInitiator.ADMIN, "Bild aus Über-uns-Sektion " + sectionId + " gelöscht");
    }

    private void validateRequiredFields(CreateAboutUsSectionRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titel darf nicht leer sein.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inhalt darf nicht leer sein.");
        }
    }

    private void validateImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;
        if (images.size() > MAX_IMAGES_PER_SECTION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Es können maximal " + MAX_IMAGES_PER_SECTION + " Bilder pro Sektion hochgeladen werden.");
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

    private void saveImages(AboutUsSection section, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return;
        for (MultipartFile upload : images) {
            AboutUsImage image = new AboutUsImage();
            image.setSection(section);
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

    private AboutUsImageResponse toImageResponse(AboutUsImage image) {
        String encoded = Base64.getEncoder().encodeToString(image.getImageData());
        String dataUrl = "data:" + image.getContentType() + ";base64," + encoded;
        return new AboutUsImageResponse(
                image.getId(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSizeBytes(),
                dataUrl,
                image.getCreatedAt());
    }

    private static final java.util.Set<String> VALID_LAYOUT_TYPES =
            java.util.Set.of("TEXT_ONLY", "IMAGE_LEFT", "IMAGE_RIGHT", "BANNER");

    private String resolveLayoutType(String value) {
        if (value != null && VALID_LAYOUT_TYPES.contains(value.toUpperCase())) {
            return value.toUpperCase();
        }
        return "TEXT_ONLY";
    }

    private AboutUsSectionResponse toResponse(AboutUsSection section) {
        List<AboutUsImageResponse> images = imageRepository.findBySectionIdOrderByCreatedAtAsc(section.getId())
                .stream().map(this::toImageResponse).toList();
        return new AboutUsSectionResponse(
                section.getId(),
                section.getTitle(),
                section.getContent(),
                section.getDisplayOrder(),
                section.getLayoutType(),
                section.getCreatedAt(),
                section.getUpdatedAt(),
                images);
    }
}
