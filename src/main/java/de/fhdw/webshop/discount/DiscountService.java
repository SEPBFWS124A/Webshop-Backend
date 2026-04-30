package de.fhdw.webshop.discount;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.discount.dto.CouponResponse;
import de.fhdw.webshop.discount.dto.CreateCouponRequest;
import de.fhdw.webshop.discount.dto.CreateDiscountRequest;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscountService implements ProductService.DiscountLookupPort {

    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final AuditLogService auditLogService;

    /** Implementation of DiscountLookupPort - returns the best active discount percent for a product. */
    @Override
    public BigDecimal findBestActiveDiscountPercent(Long customerId, Long productId) {
        List<Discount> activeDiscounts = discountRepository.findActiveDiscounts(customerId, productId, LocalDate.now());
        return activeDiscounts.stream()
                .map(Discount::getDiscountPercent)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /** US #33, #54 - Create or update a time-limited or unlimited discount for a customer. */
    @Transactional
    public DiscountResponse createDiscount(Long customerId, CreateDiscountRequest req, User salesEmployee) {
        User customer = loadCustomer(customerId);

        Discount discount = discountRepository
                .findByCustomerIdAndProductId(customerId, req.productId())
                .orElseGet(() -> {
                    Discount newDiscount = new Discount();
                    newDiscount.setCustomer(customer);
                    newDiscount.setProduct(productService.loadProduct(req.productId()));
                    return newDiscount;
                });

        discount.setDiscountPercent(req.discountPercent());
        discount.setValidFrom(req.validFrom());
        discount.setValidUntil(req.validUntil());
        discount.setCreatedByUser(salesEmployee);

        Discount saved = discountRepository.save(discount);
        auditLogService.record(
                salesEmployee,
                "CREATE_DISCOUNT",
                "Discount",
                saved.getId(),
                AuditInitiator.ADMIN,
                "customerId=" + customerId + ", productId=" + req.productId() + ", percent=" + req.discountPercent());
        return toDiscountResponse(saved);
    }

    /** US #24 - Assign a coupon to a specific customer. */
    @Transactional
    public CouponResponse createCoupon(Long customerId, CreateCouponRequest req, User salesEmployee) {
        if (couponRepository.findByCode(req.code()).isPresent()) {
            throw new IllegalArgumentException("Coupon code already exists: " + req.code());
        }

        User customer = loadCustomer(customerId);

        Coupon coupon = new Coupon();
        coupon.setCustomer(customer);
        coupon.setCode(req.code());
        coupon.setDiscountPercent(req.discountPercent());
        coupon.setValidUntil(req.validUntil());
        coupon.setCreatedByUser(salesEmployee);

        Coupon saved = couponRepository.save(coupon);
        auditLogService.record(
                salesEmployee,
                "CREATE_COUPON",
                "Coupon",
                saved.getId(),
                AuditInitiator.ADMIN,
                "code=" + req.code() + ", customerId=" + customerId + ", percent=" + req.discountPercent());
        return toCouponResponse(saved);
    }

    /** Delete a discount - only allowed for the owning customer's discounts. */
    @Transactional
    public void deleteDiscount(Long discountId, Long customerId, User salesEmployee) {
        Discount discount = discountRepository.findById(discountId)
                .orElseThrow(() -> new EntityNotFoundException("Discount not found: " + discountId));
        if (!discount.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Discount does not belong to customer: " + customerId);
        }

        discountRepository.delete(discount);
        auditLogService.record(
                salesEmployee,
                "DELETE_DISCOUNT",
                "Discount",
                discountId,
                AuditInitiator.ADMIN,
                "customerId=" + customerId + ", productId=" + discount.getProduct().getId());
    }

    /** Delete a coupon - only allowed for the owning customer's coupons. */
    @Transactional
    public void deleteCoupon(Long couponId, Long customerId, User salesEmployee) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found: " + couponId));
        if (!coupon.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Coupon does not belong to customer: " + customerId);
        }

        couponRepository.delete(coupon);
        auditLogService.record(
                salesEmployee,
                "DELETE_COUPON",
                "Coupon",
                couponId,
                AuditInitiator.ADMIN,
                "code=" + coupon.getCode() + ", customerId=" + customerId);
    }

    public List<DiscountResponse> listDiscountsForCustomer(Long customerId) {
        return discountRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toDiscountResponse)
                .toList();
    }

    public List<CouponResponse> listCouponsForCustomer(Long customerId) {
        return couponRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toCouponResponse)
                .toList();
    }

    private User loadCustomer(Long customerId) {
        return userRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found: " + customerId));
    }

    private DiscountResponse toDiscountResponse(Discount discount) {
        return new DiscountResponse(
                discount.getId(),
                discount.getCustomer().getId(),
                discount.getProduct().getId(),
                discount.getProduct().getName(),
                discount.getDiscountPercent(),
                discount.getValidFrom(),
                discount.getValidUntil());
    }

    private CouponResponse toCouponResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCustomer().getId(),
                coupon.getCode(),
                coupon.getDiscountPercent(),
                coupon.getValidUntil(),
                coupon.isUsed(),
                coupon.getUsedAt());
    }
}
