package de.fhdw.webshop.affiliate;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.affiliate.dto.AdminAffiliateApplicationResponse;
import de.fhdw.webshop.affiliate.dto.AffiliateApplicationResponse;
import de.fhdw.webshop.affiliate.dto.AffiliateLinkResponse;
import de.fhdw.webshop.affiliate.dto.TrackClickResponse;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AffiliateService {

    private static final String TRACKING_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int TRACKING_CODE_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AffiliateApplicationRepository applicationRepository;
    private final AffiliateProfileRepository profileRepository;
    private final AffiliateLinkRepository linkRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;

    // ── Bewerbungsflow ────────────────────────────────────────────────────────

    @Transactional
    public AffiliateApplicationResponse applyForAffiliate(User user, String motivationText) {
        var existing = applicationRepository.findByUser(user);

        if (existing.isPresent()) {
            AffiliateApplication app = existing.get();
            if (app.getStatus() != AffiliateApplicationStatus.REJECTED) {
                throw new IllegalStateException("Du hast bereits einen Affiliate-Antrag gestellt.");
            }
            app.setMotivationText(motivationText.trim());
            app.setStatus(AffiliateApplicationStatus.PENDING);
            app.setReviewedBy(null);
            app.setReviewNote(null);
            AffiliateApplication saved = applicationRepository.save(app);
            auditLogService.recordSystemAction(
                    "AFFILIATE_APPLICATION_RESUBMITTED", "AffiliateApplication",
                    saved.getId(), "Affiliate-Antrag neu eingereicht von: " + user.getUsername());
            return toResponse(saved);
        }

        AffiliateApplication application = new AffiliateApplication();
        application.setUser(user);
        application.setMotivationText(motivationText.trim());
        application.setStatus(AffiliateApplicationStatus.PENDING);
        AffiliateApplication saved = applicationRepository.save(application);
        auditLogService.recordSystemAction(
                "AFFILIATE_APPLICATION_SUBMITTED", "AffiliateApplication",
                saved.getId(), "Affiliate-Antrag eingereicht von: " + user.getUsername());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public AffiliateApplicationResponse getApplicationStatus(User user) {
        return applicationRepository.findByUser(user).map(this::toResponse).orElse(null);
    }

    @Transactional
    public void approveApplication(Long id, User reviewer, String note) {
        AffiliateApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Affiliate-Antrag nicht gefunden: " + id));
        if (application.getStatus() != AffiliateApplicationStatus.PENDING) {
            throw new IllegalStateException("Nur ausstehende Anträge können genehmigt werden.");
        }
        application.setStatus(AffiliateApplicationStatus.APPROVED);
        application.setReviewedBy(reviewer);
        application.setReviewNote(note);
        applicationRepository.save(application);
        userService.addRole(application.getUser(), UserRole.AFFILIATE_CUSTOMER);
        if (profileRepository.findByUser(application.getUser()).isEmpty()) {
            AffiliateProfile profile = new AffiliateProfile();
            profile.setUser(application.getUser());
            profile.setCommissionRate(new BigDecimal("0.0500"));
            profile.setActive(true);
            profileRepository.save(profile);
        }
        auditLogService.recordSystemAction("AFFILIATE_APPLICATION_APPROVED", "AffiliateApplication",
                id, "Genehmigt von " + reviewer.getUsername() + " für: " + application.getUser().getUsername());
    }

    @Transactional
    public void rejectApplication(Long id, User reviewer, String note) {
        AffiliateApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Affiliate-Antrag nicht gefunden: " + id));
        if (application.getStatus() != AffiliateApplicationStatus.PENDING) {
            throw new IllegalStateException("Nur ausstehende Anträge können abgelehnt werden.");
        }
        application.setStatus(AffiliateApplicationStatus.REJECTED);
        application.setReviewedBy(reviewer);
        application.setReviewNote(note);
        applicationRepository.save(application);
        auditLogService.recordSystemAction("AFFILIATE_APPLICATION_REJECTED", "AffiliateApplication",
                id, "Abgelehnt von " + reviewer.getUsername() + " für: " + application.getUser().getUsername());
    }

    @Transactional
    public void revokeAffiliate(Long applicationId, User admin) {
        AffiliateApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Affiliate-Antrag nicht gefunden: " + applicationId));
        if (application.getStatus() != AffiliateApplicationStatus.APPROVED) {
            throw new IllegalStateException("Nur genehmigte Affiliates können widerrufen werden.");
        }
        User affiliateUser = application.getUser();
        profileRepository.findByUser(affiliateUser).ifPresent(profile -> {
            profile.setActive(false);
            profileRepository.save(profile);
        });
        userService.removeRole(affiliateUser, UserRole.AFFILIATE_CUSTOMER);
        application.setStatus(AffiliateApplicationStatus.REJECTED);
        application.setReviewedBy(admin);
        application.setReviewNote("Affiliate-Status widerrufen von " + admin.getUsername());
        applicationRepository.save(application);
        auditLogService.recordSystemAction("AFFILIATE_REVOKED", "AffiliateApplication",
                applicationId, "Widerrufen von " + admin.getUsername() + " für: " + affiliateUser.getUsername());
    }

    @Transactional(readOnly = true)
    public List<AdminAffiliateApplicationResponse> getAllApplications() {
        return applicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminResponse).toList();
    }

    // ── Link-Generierung & Click-Tracking ────────────────────────────────────

    @Transactional
    public AffiliateLinkResponse generateLink(User user, Long productId) {
        AffiliateProfile profile = profileRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Kein Affiliate-Profil für diesen Benutzer."));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Produkt nicht gefunden: " + productId));

        String code;
        do {
            code = generateTrackingCode();
        } while (linkRepository.findByTrackingCodeAndActiveTrue(code).isPresent());

        AffiliateLink link = new AffiliateLink();
        link.setAffiliateProfile(profile);
        link.setProduct(product);
        link.setTrackingCode(code);
        AffiliateLink saved = linkRepository.save(link);

        auditLogService.recordSystemAction("AFFILIATE_LINK_GENERATED", "AffiliateLink",
                saved.getId(), "Link generiert von " + user.getUsername() + " für Produkt: " + product.getName());
        return toLinkResponse(saved);
    }

    @Transactional
    public TrackClickResponse trackClick(String trackingCode) {
        AffiliateLink link = linkRepository.findByTrackingCodeAndActiveTrue(trackingCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracking-Code nicht gefunden oder inaktiv."));
        link.setClickCount(link.getClickCount() + 1);
        linkRepository.save(link);
        return new TrackClickResponse(link.getProduct().getId());
    }

    @Transactional(readOnly = true)
    public List<AffiliateLinkResponse> getLinks(User user) {
        return profileRepository.findByUser(user)
                .map(profile -> linkRepository.findByAffiliateProfileOrderByCreatedAtDesc(profile)
                        .stream().map(this::toLinkResponse).toList())
                .orElse(Collections.emptyList());
    }

    @Transactional
    public void deactivateLink(User user, Long linkId) {
        AffiliateLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new EntityNotFoundException("Link nicht gefunden: " + linkId));
        if (!link.getAffiliateProfile().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf diesen Link.");
        }
        link.setActive(false);
        linkRepository.save(link);
        auditLogService.recordSystemAction("AFFILIATE_LINK_DEACTIVATED", "AffiliateLink",
                linkId, "Link deaktiviert von " + user.getUsername());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateTrackingCode() {
        StringBuilder sb = new StringBuilder(TRACKING_CODE_LENGTH);
        for (int i = 0; i < TRACKING_CODE_LENGTH; i++) {
            sb.append(TRACKING_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(TRACKING_CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private AffiliateLinkResponse toLinkResponse(AffiliateLink link) {
        return new AffiliateLinkResponse(
                link.getId(),
                link.getTrackingCode(),
                link.getProduct().getId(),
                link.getProduct().getName(),
                link.getProduct().getImageUrl(),
                link.getClickCount(),
                link.isActive(),
                link.getCreatedAt(),
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private AffiliateApplicationResponse toResponse(AffiliateApplication a) {
        return new AffiliateApplicationResponse(a.getId(), a.getStatus(), a.getCreatedAt(), a.getReviewNote());
    }

    private AdminAffiliateApplicationResponse toAdminResponse(AffiliateApplication a) {
        return new AdminAffiliateApplicationResponse(
                a.getId(), a.getUser().getId(), a.getUser().getUsername(), a.getUser().getEmail(),
                a.getMotivationText(), a.getStatus(),
                a.getReviewedBy() != null ? a.getReviewedBy().getUsername() : null,
                a.getReviewNote(), a.getCreatedAt());
    }
}
