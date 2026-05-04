package de.fhdw.webshop.advertisement;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.advertisement.dto.AdvertisementRequest;
import de.fhdw.webshop.advertisement.dto.AdvertisementResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdvertisementService {

    private final AdvertisementRepository advertisementRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> listAll() {
        return advertisementRepository.findAllByOrderByActiveDescCreatedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdvertisementResponse> listActive() {
        return advertisementRepository.findByActiveTrueOrderByCreatedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdvertisementResponse create(AdvertisementRequest request, User actingUser) {
        Advertisement advertisement = new Advertisement();
        applyRequest(advertisement, request);
        Advertisement savedAdvertisement = advertisementRepository.save(advertisement);
        recordAction(actingUser, "CREATE_ADVERTISEMENT", savedAdvertisement,
                "Advertisement created: " + savedAdvertisement.getTitle());
        return toResponse(savedAdvertisement);
    }

    @Transactional
    public AdvertisementResponse update(Long advertisementId, AdvertisementRequest request, User actingUser) {
        Advertisement advertisement = loadAdvertisement(advertisementId);
        applyRequest(advertisement, request);
        Advertisement savedAdvertisement = advertisementRepository.save(advertisement);
        recordAction(actingUser, "UPDATE_ADVERTISEMENT", savedAdvertisement,
                "Advertisement updated: " + savedAdvertisement.getTitle());
        return toResponse(savedAdvertisement);
    }

    @Transactional
    public AdvertisementResponse setActive(Long advertisementId, boolean active, User actingUser) {
        Advertisement advertisement = loadAdvertisement(advertisementId);
        advertisement.setActive(active);
        Advertisement savedAdvertisement = advertisementRepository.save(advertisement);
        recordAction(actingUser, "SET_ADVERTISEMENT_ACTIVE", savedAdvertisement,
                "Advertisement active set to " + active + ": " + savedAdvertisement.getTitle());
        return toResponse(savedAdvertisement);
    }

    @Transactional
    public void delete(Long advertisementId, User actingUser) {
        Advertisement advertisement = loadAdvertisement(advertisementId);
        advertisementRepository.delete(advertisement);
        recordAction(actingUser, "DELETE_ADVERTISEMENT", advertisement,
                "Advertisement deleted: " + advertisement.getTitle());
    }

    private Advertisement loadAdvertisement(Long advertisementId) {
        return advertisementRepository.findById(advertisementId)
                .orElseThrow(() -> new EntityNotFoundException("Advertisement not found: " + advertisementId));
    }

    private void applyRequest(Advertisement advertisement, AdvertisementRequest request) {
        String normalizedTitle = request.title().trim();
        String normalizedDescription = request.description().trim();
        String normalizedImageUrl = normalizeNullable(request.imageUrl());
        String normalizedTargetUrl = request.targetUrl().trim();

        if (request.contentType() == AdvertisementType.IMAGE && normalizedImageUrl == null) {
            throw new IllegalArgumentException("Für Werbeflächen vom Typ Bild ist eine Bild-URL erforderlich.");
        }

        advertisement.setTitle(normalizedTitle);
        advertisement.setDescription(normalizedDescription);
        advertisement.setContentType(request.contentType());
        advertisement.setImageUrl(normalizedImageUrl);
        advertisement.setTargetUrl(normalizedTargetUrl);
        advertisement.setActive(Boolean.TRUE.equals(request.active()));
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private AdvertisementResponse toResponse(Advertisement advertisement) {
        return new AdvertisementResponse(
                advertisement.getId(),
                advertisement.getTitle(),
                advertisement.getDescription(),
                advertisement.getContentType(),
                advertisement.getImageUrl(),
                advertisement.getTargetUrl(),
                advertisement.isActive(),
                advertisement.getCreatedAt(),
                advertisement.getUpdatedAt()
        );
    }

    private void recordAction(User actingUser, String action, Advertisement advertisement, String details) {
        auditLogService.record(
                actingUser,
                action,
                "Advertisement",
                advertisement.getId(),
                AuditInitiator.ADMIN,
                details
        );
    }
}
