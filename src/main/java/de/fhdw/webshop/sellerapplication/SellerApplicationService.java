package de.fhdw.webshop.sellerapplication;

import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.sellerapplication.dto.CreateSellerApplicationRequest;
import de.fhdw.webshop.sellerapplication.dto.SellerApplicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SellerApplicationService {

    private final SellerApplicationRepository sellerApplicationRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SellerApplicationResponse> listAll() {
        return sellerApplicationRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SellerApplicationResponse create(CreateSellerApplicationRequest request) {
        SellerApplication sellerApplication = new SellerApplication();
        sellerApplication.setCompanyName(request.companyName().trim());
        sellerApplication.setContactName(request.contactName().trim());
        sellerApplication.setEmail(request.email().trim());
        sellerApplication.setPhone(normalizeNullable(request.phone()));
        sellerApplication.setWebsite(normalizeNullable(request.website()));
        sellerApplication.setProductCategory(request.productCategory().trim());
        sellerApplication.setMessage(normalizeNullable(request.message()));
        sellerApplication.setStatus(SellerApplicationStatus.RECEIVED);

        SellerApplication savedApplication = sellerApplicationRepository.save(sellerApplication);

        auditLogService.recordSystemAction(
                "CREATE_SELLER_APPLICATION",
                "SellerApplication",
                savedApplication.getId(),
                "Seller application received from " + savedApplication.getCompanyName()
        );

        return toResponse(savedApplication);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private SellerApplicationResponse toResponse(SellerApplication sellerApplication) {
        return new SellerApplicationResponse(
                sellerApplication.getId(),
                sellerApplication.getCompanyName(),
                sellerApplication.getContactName(),
                sellerApplication.getEmail(),
                sellerApplication.getPhone(),
                sellerApplication.getWebsite(),
                sellerApplication.getProductCategory(),
                sellerApplication.getMessage(),
                sellerApplication.getStatus(),
                sellerApplication.getCreatedAt()
        );
    }
}
