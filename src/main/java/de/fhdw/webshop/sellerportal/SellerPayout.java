package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seller_payouts")
@Getter
@Setter
@NoArgsConstructor
public class SellerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_profile_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(name = "payout_number", nullable = false, unique = true, length = 40)
    private String payoutNumber;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SellerPayoutStatus status = SellerPayoutStatus.OPEN;

    @Column(name = "gross_sales_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSalesAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "open_return_holdback_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal openReturnHoldbackAmount = BigDecimal.ZERO;

    @Column(name = "settled_return_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal settledReturnAmount = BigDecimal.ZERO;

    @Column(name = "manual_adjustment_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal manualAdjustmentAmount = BigDecimal.ZERO;

    @Column(name = "net_payout_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPayoutAmount = BigDecimal.ZERO;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private User processedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    @Column(name = "corrected_at")
    private Instant correctedAt;

    @OneToMany(mappedBy = "payout", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SellerPayoutItem> items = new ArrayList<>();
}
