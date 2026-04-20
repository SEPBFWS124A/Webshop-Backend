package de.fhdw.webshop.discount;

import de.fhdw.webshop.discount.dto.CreateCouponRequest;
import de.fhdw.webshop.discount.dto.CreateDiscountRequest;
import de.fhdw.webshop.discount.dto.DiscountResponse;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountService implements ProductService.DiscountLookupPort {

    private final DiscountRepository discountRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    /** Implementation of DiscountLookupPort — returns the best active discount percent for a product. */
    @Override
    public BigDecimal findBestActiveDiscountPercent(Long customerId, Long productId) {
        List<Discount> activeDiscounts = discountRepository.findActiveDiscounts(customerId, productId, LocalDate.now());
        return activeDiscounts.stream()
                .map(Discount::getDiscountPercent)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    /** US #33, #54 — Create a time-limited or unlimited discount for a customer. */
    @Transactional
    public DiscountResponse createDiscount(Long customerId, CreateDiscountRequest createDiscountRequest, User salesEmployee) {
        User customer = loadCustomer(customerId);

        Discount discount = discountRepository
                .findByCustomerIdAndProductId(customerId, createDiscountRequest.productId())
                .orElseGet(() -> {
                    Discount newDiscount = new Discount();
                    newDiscount.setCustomer(customer);
                    newDiscount.setProduct(productService.loadProduct(createDiscountRequest.productId()));
                    return newDiscount;
                });

        discount.setDiscountPercent(createDiscountRequest.discountPercent());
        discount.setValidFrom(createDiscountRequest.validFrom());
        discount.setValidUntil(createDiscountRequest.validUntil());
        discount.setCreatedByUser(salesEmployee);

        return toDiscountResponse(discountRepository.save(discount));
    }

    /** US #24 — Assign a coupon to a specific customer. */
    @Transactional
    public Coupon createCoupon(Long customerId, CreateCouponRequest createCouponRequest, User salesEmployee) {
        if (couponRepository.findByCode(createCouponRequest.code()).isPresent()) {
            throw new IllegalArgumentException("Coupon code already exists: " + createCouponRequest.code());
        }
        User customer = loadCustomer(customerId);

        Coupon coupon = new Coupon();
        coupon.setCustomer(customer);
        coupon.setCode(createCouponRequest.code());
        coupon.setDiscountPercent(createCouponRequest.discountPercent());
        coupon.setValidUntil(createCouponRequest.validUntil());
        coupon.setCreatedByUser(salesEmployee);
        return couponRepository.save(coupon);
    }

    public List<DiscountResponse> listDiscountsForCustomer(Long customerId) {
        return discountRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toDiscountResponse)
                .toList();
    }

    public List<Coupon> listCouponsForCustomer(Long customerId) {
        return couponRepository.findByCustomerId(customerId);
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
                discount.getDiscountPercent(),
                discount.getValidFrom(),
                discount.getValidUntil()
        );
    }
}
