package de.fhdw.webshop.sellerportal;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.marketplacedispute.MarketplaceDispute;
import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeRepository;
import de.fhdw.webshop.marketplacedispute.MarketplaceDisputeStatus;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderStatus;
import de.fhdw.webshop.returnrequest.ReturnRequest;
import de.fhdw.webshop.returnrequest.ReturnRequestItem;
import de.fhdw.webshop.returnrequest.ReturnRequestStatus;
import de.fhdw.webshop.sellerportal.dto.SellerDashboardResponse;
import de.fhdw.webshop.sellerportal.dto.SellerOrderSummaryResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutCorrectionRequest;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutDetailResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutItemResponse;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutReviewRequest;
import de.fhdw.webshop.sellerportal.dto.SellerPayoutSummaryResponse;
import de.fhdw.webshop.sellerportal.dto.SellerProfileResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRole;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerPortalService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter PERIOD_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MM.yyyy").withLocale(Locale.GERMANY);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    private final SellerProfileRepository sellerProfileRepository;
    private final SellerPayoutRepository sellerPayoutRepository;
    private final SellerPayoutItemRepository sellerPayoutItemRepository;
    private final SellerPortalOrderItemRepository sellerPortalOrderItemRepository;
    private final SellerPortalReturnRequestItemRepository sellerPortalReturnRequestItemRepository;
    private final MarketplaceDisputeRepository marketplaceDisputeRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public SellerDashboardResponse getDashboard(User sellerUser) {
        SellerProfile sellerProfile = requireSellerProfile(sellerUser);
        synchronizeSellerPayouts(sellerProfile);

        SellerSnapshot snapshot = buildSnapshot(sellerProfile);
        List<SellerOrderSummaryResponse> orders = buildOrderSummaries(snapshot);
        List<SellerPayout> payouts = sellerPayoutRepository.findBySellerProfileIdOrderByPeriodStartDesc(sellerProfile.getId());

        long deliveredOrders = orders.stream().filter(order -> order.status() == OrderStatus.DELIVERED).count();
        long totalOrders = orders.size();
        long returnRequests = snapshot.returnItems().stream()
                .map(item -> item.getReturnRequest().getId())
                .distinct()
                .count();
        BigDecimal grossRevenue = money(orders.stream()
                .filter(order -> order.status() == OrderStatus.DELIVERED)
                .map(SellerOrderSummaryResponse::grossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal netRevenue = money(orders.stream()
                .filter(order -> order.status() == OrderStatus.DELIVERED)
                .map(order -> order.netAmount()
                        .subtract(order.openReturnHoldbackAmount())
                        .subtract(order.settledReturnAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<SellerPayout> openPayouts = payouts.stream()
                .filter(payout -> payout.getStatus() != SellerPayoutStatus.PAID_OUT)
                .toList();
        BigDecimal openPayoutAmount = money(openPayouts.stream()
                .map(SellerPayout::getNetPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal returnRate = deliveredOrders == 0
                ? BigDecimal.ZERO
                : money(BigDecimal.valueOf(returnRequests)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(deliveredOrders), 2, RoundingMode.HALF_UP));

        return new SellerDashboardResponse(
                toProfileResponse(sellerProfile),
                totalOrders,
                deliveredOrders,
                returnRequests,
                grossRevenue,
                netRevenue,
                returnRate,
                openPayouts.size(),
                openPayoutAmount
        );
    }

    @Transactional
    public List<SellerOrderSummaryResponse> listSellerOrders(
            User sellerUser,
            OrderStatus status,
            LocalDate from,
            LocalDate to
    ) {
        SellerProfile sellerProfile = requireSellerProfile(sellerUser);
        SellerSnapshot snapshot = buildSnapshot(sellerProfile);
        return buildOrderSummaries(snapshot).stream()
                .filter(order -> status == null || order.status() == status)
                .filter(order -> matchesDateRange(order.orderedAt(), from, to))
                .toList();
    }

    @Transactional
    public List<SellerPayoutSummaryResponse> listSellerPayouts(
            User sellerUser,
            SellerPayoutStatus status,
            LocalDate from,
            LocalDate to
    ) {
        SellerProfile sellerProfile = requireSellerProfile(sellerUser);
        synchronizeSellerPayouts(sellerProfile);
        return sellerPayoutRepository.findBySellerProfileIdOrderByPeriodStartDesc(sellerProfile.getId()).stream()
                .filter(payout -> matchesPayoutFilters(payout, status, from, to))
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional
    public SellerPayoutDetailResponse getSellerPayoutDetail(User sellerUser, Long payoutId) {
        SellerProfile sellerProfile = requireSellerProfile(sellerUser);
        synchronizeSellerPayouts(sellerProfile);
        SellerPayout payout = sellerPayoutRepository.findByIdAndSellerProfileUserId(payoutId, sellerUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        return toDetailResponse(payout);
    }

    @Transactional
    public byte[] downloadSellerPayoutCsv(User sellerUser, Long payoutId) {
        SellerPayoutDetailResponse detail = getSellerPayoutDetail(sellerUser, payoutId);
        StringBuilder csv = new StringBuilder();
        csv.append("Typ;Datum;Referenz;Bestellung;Retoure;Beschreibung;Menge;Brutto;Rabatt;Provision;Gebühren;Rückbehalt;Rücksendung;Korrektur;Netto\n");
        for (SellerPayoutItemResponse item : detail.items()) {
            csv.append(csv(item.type().name())).append(';')
                    .append(csv(formatDateTime(item.occurredAt()))).append(';')
                    .append(csv(item.referenceNumber())).append(';')
                    .append(csv(item.orderNumber())).append(';')
                    .append(csv(item.returnNumber())).append(';')
                    .append(csv(item.description())).append(';')
                    .append(item.quantity()).append(';')
                    .append(csv(formatMoney(item.grossAmount()))).append(';')
                    .append(csv(formatMoney(item.discountAmount()))).append(';')
                    .append(csv(formatMoney(item.commissionAmount()))).append(';')
                    .append(csv(formatMoney(item.feeAmount()))).append(';')
                    .append(csv(formatMoney(item.returnHoldbackAmount()))).append(';')
                    .append(csv(formatMoney(item.returnSettlementAmount()))).append(';')
                    .append(csv(formatMoney(item.manualAdjustmentAmount()))).append(';')
                    .append(csv(formatMoney(item.netAmount()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public byte[] downloadSellerPayoutPdf(User sellerUser, Long payoutId) {
        SellerPayoutDetailResponse detail = getSellerPayoutDetail(sellerUser, payoutId);
        return PdfDocumentBuilder.build(detail);
    }

    @Transactional
    public List<SellerPayoutSummaryResponse> listAdminPayouts(
            Long sellerProfileId,
            SellerPayoutStatus status,
            LocalDate from,
            LocalDate to
    ) {
        synchronizeAllPayouts();
        return sellerPayoutRepository.findAllByOrderByPeriodStartDescIdDesc().stream()
                .filter(payout -> sellerProfileId == null || Objects.equals(payout.getSellerProfile().getId(), sellerProfileId))
                .filter(payout -> matchesPayoutFilters(payout, status, from, to))
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional
    public SellerPayoutDetailResponse getAdminPayoutDetail(Long payoutId) {
        synchronizeAllPayouts();
        SellerPayout payout = sellerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        return toDetailResponse(payout);
    }

    @Transactional
    public SellerPayoutDetailResponse movePayoutToReview(Long payoutId, SellerPayoutReviewRequest request, User employee) {
        SellerPayout payout = sellerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        ensureMutablePayout(payout);
        payout.setStatus(SellerPayoutStatus.IN_REVIEW);
        payout.setAdminNote(blankToNull(request == null ? null : request.note()));
        payout.setProcessedBy(employee);
        sellerPayoutRepository.save(payout);
        auditLogService.record(employee, "SELLER_PAYOUT_IN_REVIEW", "SellerPayout", payoutId,
                AuditInitiator.ADMIN, "Auszahlung " + payout.getPayoutNumber() + " auf In Prüfung gesetzt.");
        synchronizeSellerPayouts(payout.getSellerProfile());
        return getAdminPayoutDetail(payoutId);
    }

    @Transactional
    public SellerPayoutDetailResponse approvePayout(Long payoutId, SellerPayoutReviewRequest request, User employee) {
        SellerPayout payout = sellerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        ensureMutablePayout(payout);
        ensureNoOpenDisputeBlock(payout);
        payout.setStatus(SellerPayoutStatus.APPROVED);
        payout.setAdminNote(blankToNull(request == null ? null : request.note()));
        payout.setApprovedAt(Instant.now());
        payout.setProcessedBy(employee);
        sellerPayoutRepository.save(payout);
        auditLogService.record(employee, "SELLER_PAYOUT_APPROVED", "SellerPayout", payoutId,
                AuditInitiator.ADMIN, "Auszahlung " + payout.getPayoutNumber() + " freigegeben.");
        synchronizeSellerPayouts(payout.getSellerProfile());
        return getAdminPayoutDetail(payoutId);
    }

    @Transactional
    public SellerPayoutDetailResponse correctPayout(Long payoutId, SellerPayoutCorrectionRequest request, User employee) {
        SellerPayout payout = sellerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        ensureMutablePayout(payout);
        payout.setStatus(SellerPayoutStatus.CORRECTED);
        payout.setCorrectionReason(request.reason().trim());
        payout.setManualAdjustmentAmount(money(request.manualAdjustmentAmount() == null
                ? BigDecimal.ZERO
                : request.manualAdjustmentAmount()));
        payout.setAdminNote(blankToNull(request.note()));
        payout.setCorrectedAt(Instant.now());
        payout.setProcessedBy(employee);
        sellerPayoutRepository.save(payout);
        auditLogService.record(employee, "SELLER_PAYOUT_CORRECTED", "SellerPayout", payoutId,
                AuditInitiator.ADMIN,
                "Auszahlung " + payout.getPayoutNumber() + " korrigiert: " + payout.getCorrectionReason());
        synchronizeSellerPayouts(payout.getSellerProfile());
        return getAdminPayoutDetail(payoutId);
    }

    @Transactional
    public SellerPayoutDetailResponse markPayoutAsPaidOut(Long payoutId, SellerPayoutReviewRequest request, User employee) {
        SellerPayout payout = sellerPayoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Auszahlung nicht gefunden: " + payoutId));
        if (payout.getStatus() == SellerPayoutStatus.PAID_OUT) {
            return toDetailResponse(payout);
        }
        ensureNoOpenDisputeBlock(payout);
        payout.setStatus(SellerPayoutStatus.PAID_OUT);
        payout.setPaidOutAt(Instant.now());
        payout.setAdminNote(blankToNull(request == null ? null : request.note()));
        payout.setProcessedBy(employee);
        sellerPayoutRepository.save(payout);
        auditLogService.record(employee, "SELLER_PAYOUT_PAID_OUT", "SellerPayout", payoutId,
                AuditInitiator.ADMIN, "Auszahlung " + payout.getPayoutNumber() + " als ausgezahlt markiert.");
        return toDetailResponse(payout);
    }

    private SellerProfile requireSellerProfile(User sellerUser) {
        if (sellerUser == null || !sellerUser.hasRole(UserRole.SELLER)) {
            throw new IllegalArgumentException("Nur Marketplace-Verkäufer können das Verkäuferportal nutzen.");
        }
        return sellerProfileRepository.findByUserId(sellerUser.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Kein aktives Verkäuferprofil für Benutzer " + sellerUser.getUsername() + " gefunden."));
    }

    private boolean matchesPayoutFilters(SellerPayout payout, SellerPayoutStatus status, LocalDate from, LocalDate to) {
        if (status != null && payout.getStatus() != status) {
            return false;
        }
        if (from != null && payout.getPeriodEnd().isBefore(from)) {
            return false;
        }
        if (to != null && payout.getPeriodStart().isAfter(to)) {
            return false;
        }
        return true;
    }

    private boolean matchesDateRange(Instant value, LocalDate from, LocalDate to) {
        if (value == null) {
            return from == null && to == null;
        }
        LocalDate date = value.atZone(ZoneId.of("Europe/Berlin")).toLocalDate();
        if (from != null && date.isBefore(from)) {
            return false;
        }
        if (to != null && date.isAfter(to)) {
            return false;
        }
        return true;
    }

    private void synchronizeAllPayouts() {
        sellerProfileRepository.findAllByActiveTrueOrderByDisplayNameAsc()
                .forEach(this::synchronizeSellerPayouts);
    }

    private void synchronizeSellerPayouts(SellerProfile sellerProfile) {
        SellerSnapshot snapshot = buildSnapshot(sellerProfile);
        Map<LocalDate, List<PayoutComputationItem>> itemsByPeriod = new LinkedHashMap<>();
        snapshot.orderItems().stream()
                .filter(orderItem -> orderItem.getOrder().getStatus() == OrderStatus.DELIVERED)
                .forEach(orderItem -> {
                    LineAmounts amounts = snapshot.lineAmountsByOrderItemId().get(orderItem.getId());
                    if (amounts == null) {
                        return;
                    }
                    Instant occurredAt = resolveSaleInstant(orderItem.getOrder());
                    LocalDate periodStart = occurredAt.atZone(ZoneId.of("Europe/Berlin")).toLocalDate().withDayOfMonth(1);
                    addComputationItem(itemsByPeriod, periodStart, new PayoutComputationItem(
                            SellerPayoutItemType.SALE,
                            occurredAt,
                            orderItem.getOrder().getOrderNumber(),
                            orderItem.getProduct().getName(),
                            orderItem.getOrder(),
                            null,
                            orderItem.getQuantity(),
                            amounts.grossAmount(),
                            amounts.discountAmount(),
                            amounts.commissionAmount(),
                            amounts.feeAmount(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            amounts.netAmount()
                    ));
                });

        snapshot.returnImpacts().values().stream()
                .flatMap(Collection::stream)
                .forEach(impact -> {
                    LocalDate periodStart = impact.occurredAt()
                            .atZone(ZoneId.of("Europe/Berlin"))
                            .toLocalDate()
                            .withDayOfMonth(1);
                    addComputationItem(itemsByPeriod, periodStart, new PayoutComputationItem(
                            impact.type(),
                            impact.occurredAt(),
                            "RMA-" + impact.returnRequest().getId(),
                            impact.description(),
                            impact.order(),
                            impact.returnRequest(),
                            impact.quantity(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            impact.type() == SellerPayoutItemType.RETURN_HOLDBACK ? impact.amount() : BigDecimal.ZERO,
                            impact.type() == SellerPayoutItemType.RETURN_SETTLEMENT ? impact.amount() : BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            impact.amount().negate()
                    ));
                });

        snapshot.activeDisputes().forEach(dispute -> {
            LineAmounts amounts = snapshot.lineAmountsByOrderItemId().get(dispute.getOrderItem().getId());
            if (amounts == null) {
                return;
            }
            Instant occurredAt = dispute.getCreatedAt() != null ? dispute.getCreatedAt() : resolveSaleInstant(dispute.getOrder());
            LocalDate periodStart = resolveSaleInstant(dispute.getOrder())
                    .atZone(ZoneId.of("Europe/Berlin"))
                    .toLocalDate()
                    .withDayOfMonth(1);
            BigDecimal holdbackAmount = money(amounts.netAmount());
            addComputationItem(itemsByPeriod, periodStart, new PayoutComputationItem(
                    SellerPayoutItemType.DISPUTE_HOLDBACK,
                    occurredAt,
                    "DSP-" + dispute.getId(),
                    dispute.getOrderItem().getProduct().getName() + " | Konfliktfall " + dispute.getStatus(),
                    dispute.getOrder(),
                    null,
                    dispute.getOrderItem().getQuantity(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    holdbackAmount,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    holdbackAmount.negate()
            ));
        });

        Map<LocalDate, SellerPayout> existingByPeriod = sellerPayoutRepository
                .findBySellerProfileIdOrderByPeriodStartDesc(sellerProfile.getId())
                .stream()
                .collect(Collectors.toMap(SellerPayout::getPeriodStart, payout -> payout, (left, right) -> left));

        Set<LocalDate> periods = new HashSet<>(itemsByPeriod.keySet());
        periods.addAll(existingByPeriod.keySet());

        periods.stream()
                .sorted(Comparator.reverseOrder())
                .forEach(periodStart -> synchronizeSinglePayout(
                        sellerProfile,
                        periodStart,
                        itemsByPeriod.getOrDefault(periodStart, List.of()),
                        existingByPeriod.get(periodStart)));
    }

    private void synchronizeSinglePayout(
            SellerProfile sellerProfile,
            LocalDate periodStart,
            List<PayoutComputationItem> computedItems,
            SellerPayout existingPayout
    ) {
        if (existingPayout != null && existingPayout.getStatus() == SellerPayoutStatus.PAID_OUT) {
            return;
        }

        List<PayoutComputationItem> itemsToPersist = new ArrayList<>(computedItems);
        if (existingPayout != null && existingPayout.getManualAdjustmentAmount().compareTo(BigDecimal.ZERO) != 0) {
            itemsToPersist.add(buildCorrectionItem(existingPayout));
        }

        if (itemsToPersist.isEmpty()) {
            if (existingPayout != null) {
                sellerPayoutItemRepository.deleteByPayoutId(existingPayout.getId());
                sellerPayoutRepository.delete(existingPayout);
            }
            return;
        }

        SellerPayout payout = existingPayout == null ? new SellerPayout() : existingPayout;
        if (existingPayout == null) {
            payout.setSellerProfile(sellerProfile);
            payout.setPayoutNumber(generatePayoutNumber(sellerProfile, periodStart));
            payout.setStatus(SellerPayoutStatus.OPEN);
        }
        payout.setPeriodStart(periodStart);
        payout.setPeriodEnd(periodStart.with(TemporalAdjusters.lastDayOfMonth()));

        BigDecimal gross = sum(itemsToPersist, PayoutComputationItem::grossAmount);
        BigDecimal discount = sum(itemsToPersist, PayoutComputationItem::discountAmount);
        BigDecimal commission = sum(itemsToPersist, PayoutComputationItem::commissionAmount);
        BigDecimal fee = sum(itemsToPersist, PayoutComputationItem::feeAmount);
        BigDecimal holdback = sum(itemsToPersist, PayoutComputationItem::returnHoldbackAmount);
        BigDecimal settled = sum(itemsToPersist, PayoutComputationItem::returnSettlementAmount);
        BigDecimal manualAdjustment = sum(itemsToPersist, PayoutComputationItem::manualAdjustmentAmount);
        BigDecimal net = sum(itemsToPersist, PayoutComputationItem::netAmount);
        boolean payoutBlocked = itemsToPersist.stream().anyMatch(item -> item.type() == SellerPayoutItemType.DISPUTE_HOLDBACK);

        payout.setGrossSalesAmount(gross);
        payout.setDiscountAmount(discount);
        payout.setCommissionAmount(commission);
        payout.setFeeAmount(fee);
        payout.setOpenReturnHoldbackAmount(holdback);
        payout.setSettledReturnAmount(settled);
        payout.setManualAdjustmentAmount(manualAdjustment);
        payout.setNetPayoutAmount(net);
        payout.setPayoutBlocked(payoutBlocked);
        sellerPayoutRepository.save(payout);

        if (payout.getId() != null) {
            sellerPayoutItemRepository.deleteByPayoutId(payout.getId());
        }
        sellerPayoutItemRepository.saveAll(itemsToPersist.stream()
                .sorted(Comparator.comparing(PayoutComputationItem::occurredAt).thenComparing(PayoutComputationItem::description))
                .map(item -> toEntity(payout, item))
                .toList());
    }

    private SellerSnapshot buildSnapshot(SellerProfile sellerProfile) {
        List<OrderItem> orderItems = sellerPortalOrderItemRepository
                .findBySellerNameIgnoreCaseOrderByOrderCreatedAtDesc(sellerProfile.getDisplayName());
        List<ReturnRequestItem> returnItems = sellerPortalReturnRequestItemRepository.findBySellerName(sellerProfile.getDisplayName());
        List<MarketplaceDispute> activeDisputes = marketplaceDisputeRepository.findBySellerNameIgnoreCaseAndStatusIn(
                sellerProfile.getDisplayName(),
                Set.of(MarketplaceDisputeStatus.OPEN, MarketplaceDisputeStatus.UNDER_REVIEW));
        Map<Long, BigDecimal> subtotalCache = new HashMap<>();
        Map<Long, LineAmounts> lineAmountsByOrderItemId = new HashMap<>();

        for (OrderItem orderItem : orderItems) {
            lineAmountsByOrderItemId.put(orderItem.getId(), calculateLineAmounts(orderItem, sellerProfile, subtotalCache));
        }

        Map<Long, List<ReturnImpact>> returnImpacts = new HashMap<>();
        for (ReturnRequestItem returnItem : returnItems) {
            ReturnImpact impact = calculateReturnImpact(returnItem, lineAmountsByOrderItemId.get(returnItem.getOrderItem().getId()));
            if (impact == null) {
                continue;
            }
            returnImpacts.computeIfAbsent(returnItem.getOrderItem().getId(), ignored -> new ArrayList<>()).add(impact);
        }

        return new SellerSnapshot(orderItems, returnItems, activeDisputes, lineAmountsByOrderItemId, returnImpacts);
    }

    private LineAmounts calculateLineAmounts(
            OrderItem orderItem,
            SellerProfile sellerProfile,
            Map<Long, BigDecimal> subtotalCache
    ) {
        BigDecimal gross = money(orderItem.getPriceAtOrderTime().multiply(BigDecimal.valueOf(orderItem.getQuantity())));
        BigDecimal subtotal = subtotalCache.computeIfAbsent(orderItem.getOrder().getId(),
                ignored -> computeOrderSubtotal(orderItem.getOrder()));
        BigDecimal discount = allocate(orderItem.getOrder().getDiscountAmount(), gross, subtotal);
        BigDecimal fee = allocate(orderItem.getOrder().getShippingCost(), gross, subtotal);
        BigDecimal commissionBase = gross.subtract(discount);
        BigDecimal commission = money(commissionBase
                .multiply(sellerProfile.getCommissionRate())
                .divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal net = money(gross.subtract(discount).subtract(commission).subtract(fee));
        return new LineAmounts(gross, discount, commission, fee, net);
    }

    private ReturnImpact calculateReturnImpact(ReturnRequestItem returnItem, LineAmounts lineAmounts) {
        if (lineAmounts == null) {
            return null;
        }

        ReturnRequest request = returnItem.getReturnRequest();
        if (request.getStatus() == ReturnRequestStatus.REJECTED) {
            return null;
        }

        BigDecimal lineNetPerUnit = lineAmounts.netAmount()
                .divide(BigDecimal.valueOf(Math.max(1, returnItem.getOrderItem().getQuantity())), 6, RoundingMode.HALF_UP);
        BigDecimal amount = money(lineNetPerUnit.multiply(BigDecimal.valueOf(returnItem.getQuantity())));
        SellerPayoutItemType type = isSettledReturn(request)
                ? SellerPayoutItemType.RETURN_SETTLEMENT
                : SellerPayoutItemType.RETURN_HOLDBACK;
        Instant occurredAt = type == SellerPayoutItemType.RETURN_SETTLEMENT
                ? resolveSettledReturnInstant(request)
                : request.getCreatedAt();
        String description = returnItem.getProductName() + " | " + describeReturnStatus(request.getStatus());
        return new ReturnImpact(
                type,
                occurredAt,
                amount,
                returnItem.getQuantity(),
                description,
                returnItem.getOrderItem().getOrder(),
                request
        );
    }

    private boolean isSettledReturn(ReturnRequest request) {
        return request.getStatus() == ReturnRequestStatus.REFUNDED
                || request.getStatus() == ReturnRequestStatus.COMPLETED;
    }

    private Instant resolveSettledReturnInstant(ReturnRequest request) {
        if (request.getRefundedAt() != null) {
            return request.getRefundedAt();
        }
        if (request.getApprovedAt() != null) {
            return request.getApprovedAt();
        }
        if (request.getGoodsReceivedAt() != null) {
            return request.getGoodsReceivedAt();
        }
        return request.getCreatedAt();
    }

    private BigDecimal computeOrderSubtotal(Order order) {
        return money(order.getItems().stream()
                .map(item -> item.getPriceAtOrderTime().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal allocate(BigDecimal total, BigDecimal lineAmount, BigDecimal subtotal) {
        if (total == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return money(total.multiply(lineAmount).divide(subtotal, 6, RoundingMode.HALF_UP));
    }

    private List<SellerOrderSummaryResponse> buildOrderSummaries(SellerSnapshot snapshot) {
        Map<Long, MutableOrderSummary> byOrderId = new LinkedHashMap<>();
        for (OrderItem orderItem : snapshot.orderItems()) {
            Order order = orderItem.getOrder();
            MutableOrderSummary summary = byOrderId.computeIfAbsent(order.getId(),
                    ignored -> new MutableOrderSummary(order));
            LineAmounts amounts = snapshot.lineAmountsByOrderItemId().get(orderItem.getId());
            if (amounts != null) {
                summary.gross = summary.gross.add(amounts.grossAmount());
                summary.discount = summary.discount.add(amounts.discountAmount());
                summary.commission = summary.commission.add(amounts.commissionAmount());
                summary.fee = summary.fee.add(amounts.feeAmount());
                summary.net = summary.net.add(amounts.netAmount());
            }
            summary.itemCount++;
            summary.quantity += orderItem.getQuantity();

            for (ReturnImpact impact : snapshot.returnImpacts().getOrDefault(orderItem.getId(), List.of())) {
                if (impact.type() == SellerPayoutItemType.RETURN_SETTLEMENT) {
                    summary.settledReturn = summary.settledReturn.add(impact.amount());
                    summary.hasSettledReturn = true;
                } else {
                    summary.openReturnHoldback = summary.openReturnHoldback.add(impact.amount());
                    summary.hasActiveReturn = true;
                }
            }
            snapshot.activeDisputes().stream()
                    .filter(dispute -> Objects.equals(dispute.getOrderItem().getId(), orderItem.getId()))
                    .findAny()
                    .ifPresent(dispute -> {
                        LineAmounts disputeAmounts = snapshot.lineAmountsByOrderItemId().get(orderItem.getId());
                        if (disputeAmounts != null) {
                            summary.openReturnHoldback = summary.openReturnHoldback.add(disputeAmounts.netAmount());
                            summary.payoutBlocked = true;
                        }
                    });
        }

        return byOrderId.values().stream()
                .map(MutableOrderSummary::toResponse)
                .sorted(Comparator.comparing(SellerOrderSummaryResponse::orderedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(SellerOrderSummaryResponse::orderNumber, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private SellerPayoutSummaryResponse toSummaryResponse(SellerPayout payout) {
        return new SellerPayoutSummaryResponse(
                payout.getId(),
                payout.getPayoutNumber(),
                payout.getSellerProfile().getId(),
                payout.getSellerProfile().getDisplayName(),
                payout.getPeriodStart(),
                payout.getPeriodEnd(),
                payout.getStatus(),
                money(payout.getGrossSalesAmount()),
                money(payout.getDiscountAmount()),
                money(payout.getCommissionAmount()),
                money(payout.getFeeAmount()),
                money(payout.getOpenReturnHoldbackAmount()),
                money(payout.getSettledReturnAmount()),
                money(payout.getManualAdjustmentAmount()),
                money(payout.getNetPayoutAmount()),
                payout.isPayoutBlocked(),
                payout.getCreatedAt(),
                payout.getApprovedAt(),
                payout.getPaidOutAt(),
                payout.getCorrectedAt(),
                payout.getCorrectionReason(),
                payout.getAdminNote(),
                payout.getProcessedBy() == null ? null : payout.getProcessedBy().getUsername()
        );
    }

    private SellerPayoutDetailResponse toDetailResponse(SellerPayout payout) {
        List<SellerPayoutItemResponse> items = sellerPayoutItemRepository.findByPayoutIdOrderByOccurredAtAscIdAsc(payout.getId())
                .stream()
                .map(item -> new SellerPayoutItemResponse(
                        item.getId(),
                        item.getItemType(),
                        item.getOccurredAt(),
                        item.getReferenceNumber(),
                        item.getDescription(),
                        item.getOrder() == null ? null : item.getOrder().getOrderNumber(),
                        item.getReturnRequest() == null ? null : "RMA-" + item.getReturnRequest().getId(),
                        item.getQuantity(),
                        money(item.getGrossAmount()),
                        money(item.getDiscountAmount()),
                        money(item.getCommissionAmount()),
                        money(item.getFeeAmount()),
                        money(item.getReturnHoldbackAmount()),
                        money(item.getReturnSettlementAmount()),
                        money(item.getManualAdjustmentAmount()),
                        money(item.getNetAmount())
                ))
                .toList();
        return new SellerPayoutDetailResponse(toSummaryResponse(payout), items);
    }

    private SellerProfileResponse toProfileResponse(SellerProfile sellerProfile) {
        return new SellerProfileResponse(
                sellerProfile.getId(),
                sellerProfile.getDisplayName(),
                money(sellerProfile.getCommissionRate()),
                maskIban(sellerProfile.getPayoutIban())
        );
    }

    private String maskIban(String iban) {
        if (iban == null || iban.length() < 6) {
            return iban;
        }
        return iban.substring(0, 4) + " **** **** **** " + iban.substring(iban.length() - 4);
    }

    private Instant resolveSaleInstant(Order order) {
        return order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getCreatedAt();
    }

    private String describeReturnStatus(ReturnRequestStatus status) {
        return switch (status) {
            case SUBMITTED -> "Rücksendeanfrage offen";
            case GOODS_RECEIVED -> "Ware eingegangen";
            case IN_REVIEW -> "In Prüfung";
            case APPROVED -> "Freigegeben";
            case REFUNDED, COMPLETED -> "Rückerstattung abgeschlossen";
            case REJECTED -> "Abgelehnt";
        };
    }

    private SellerPayoutItem toEntity(SellerPayout payout, PayoutComputationItem item) {
        SellerPayoutItem entity = new SellerPayoutItem();
        entity.setPayout(payout);
        entity.setOrder(item.order());
        entity.setReturnRequest(item.returnRequest());
        entity.setItemType(item.type());
        entity.setReferenceNumber(item.referenceNumber());
        entity.setDescription(item.description());
        entity.setOccurredAt(item.occurredAt());
        entity.setQuantity(item.quantity());
        entity.setGrossAmount(money(item.grossAmount()));
        entity.setDiscountAmount(money(item.discountAmount()));
        entity.setCommissionAmount(money(item.commissionAmount()));
        entity.setFeeAmount(money(item.feeAmount()));
        entity.setReturnHoldbackAmount(money(item.returnHoldbackAmount()));
        entity.setReturnSettlementAmount(money(item.returnSettlementAmount()));
        entity.setManualAdjustmentAmount(money(item.manualAdjustmentAmount()));
        entity.setNetAmount(money(item.netAmount()));
        return entity;
    }

    private PayoutComputationItem buildCorrectionItem(SellerPayout payout) {
        Instant occurredAt = payout.getCorrectedAt() != null ? payout.getCorrectedAt() : payout.getCreatedAt();
        return new PayoutComputationItem(
                SellerPayoutItemType.CORRECTION,
                occurredAt,
                payout.getPayoutNumber(),
                blankToDash(payout.getCorrectionReason()),
                null,
                null,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                money(payout.getManualAdjustmentAmount()),
                money(payout.getManualAdjustmentAmount())
        );
    }

    private void ensureMutablePayout(SellerPayout payout) {
        if (payout.getStatus() == SellerPayoutStatus.PAID_OUT) {
            throw new IllegalStateException("Bereits ausgezahlte Abrechnungen können nicht mehr geändert werden.");
        }
    }

    private void ensureNoOpenDisputeBlock(SellerPayout payout) {
        if (payout.isPayoutBlocked()) {
            throw new IllegalStateException("Diese Auszahlung ist wegen offener Marketplace-Konfliktfälle blockiert.");
        }
    }

    private String generatePayoutNumber(SellerProfile sellerProfile, LocalDate periodStart) {
        return "SPA-" + periodStart.format(DateTimeFormatter.ofPattern("yyyyMM"))
                + "-" + String.format("%03d", sellerProfile.getId());
    }

    private void addComputationItem(
            Map<LocalDate, List<PayoutComputationItem>> itemsByPeriod,
            LocalDate periodStart,
            PayoutComputationItem item
    ) {
        itemsByPeriod.computeIfAbsent(periodStart, ignored -> new ArrayList<>()).add(item);
    }

    private BigDecimal sum(List<PayoutComputationItem> items, java.util.function.Function<PayoutComputationItem, BigDecimal> extractor) {
        return money(items.stream()
                .map(extractor)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        return money(value).toPlainString() + " EUR";
    }

    private String formatDateTime(Instant value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record SellerSnapshot(
            List<OrderItem> orderItems,
            List<ReturnRequestItem> returnItems,
            List<MarketplaceDispute> activeDisputes,
            Map<Long, LineAmounts> lineAmountsByOrderItemId,
            Map<Long, List<ReturnImpact>> returnImpacts
    ) {
    }

    private record LineAmounts(
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal commissionAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount
    ) {
    }

    private record ReturnImpact(
            SellerPayoutItemType type,
            Instant occurredAt,
            BigDecimal amount,
            int quantity,
            String description,
            Order order,
            ReturnRequest returnRequest
    ) {
    }

    private record PayoutComputationItem(
            SellerPayoutItemType type,
            Instant occurredAt,
            String referenceNumber,
            String description,
            Order order,
            ReturnRequest returnRequest,
            int quantity,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal commissionAmount,
            BigDecimal feeAmount,
            BigDecimal returnHoldbackAmount,
            BigDecimal returnSettlementAmount,
            BigDecimal manualAdjustmentAmount,
            BigDecimal netAmount
    ) {
    }

    private static final class MutableOrderSummary {
        private final Order order;
        private int itemCount;
        private int quantity;
        private BigDecimal gross = BigDecimal.ZERO;
        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private BigDecimal fee = BigDecimal.ZERO;
        private BigDecimal openReturnHoldback = BigDecimal.ZERO;
        private BigDecimal settledReturn = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;
        private boolean hasActiveReturn;
        private boolean hasSettledReturn;
        private boolean payoutBlocked;

        private MutableOrderSummary(Order order) {
            this.order = order;
        }

        private SellerOrderSummaryResponse toResponse() {
            return new SellerOrderSummaryResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getCustomerName(),
                    order.getStatus(),
                    order.getCreatedAt(),
                    order.getDeliveredAt(),
                    itemCount,
                    quantity,
                    gross.setScale(2, RoundingMode.HALF_UP),
                    discount.setScale(2, RoundingMode.HALF_UP),
                    commission.setScale(2, RoundingMode.HALF_UP),
                    fee.setScale(2, RoundingMode.HALF_UP),
                    openReturnHoldback.setScale(2, RoundingMode.HALF_UP),
                    settledReturn.setScale(2, RoundingMode.HALF_UP),
                    net.setScale(2, RoundingMode.HALF_UP),
                    hasActiveReturn,
                    hasSettledReturn,
                    payoutBlocked,
                    order.getStatus() == OrderStatus.CANCELLED
            );
        }
    }

    private static final class PdfDocumentBuilder {

        private PdfDocumentBuilder() {
        }

        static byte[] build(SellerPayoutDetailResponse detail) {
            SellerPayoutSummaryResponse payout = detail.payout();
            StringBuilder content = new StringBuilder();

            rect(content, 0, 792, 595, 50, "15 23 42", true);
            text(content, "Webshop Marketplace", 50, 812, 20, true, "255 255 255");
            text(content, "Verkäuferabrechnung", 50, 758, 24, true, "15 23 42");
            text(content, "Abrechnung " + payout.payoutNumber(), 50, 738, 11, false, "71 85 105");

            rect(content, 405, 710, 140, 36, statusColor(payout.status()), true);
            text(content, statusLabel(payout.status()), 420, 730, 12, true, "255 255 255");
            text(content, "Status", 420, 716, 8, false, "226 232 240");

            text(content, "Verkäufer", 50, 700, 9, true, "100 116 139");
            text(content, payout.sellerDisplayName(), 50, 684, 14, true, "15 23 42");
            text(content, "Zeitraum", 50, 656, 9, true, "100 116 139");
            text(content, formatDate(payout.periodStart()) + " bis " + formatDate(payout.periodEnd()), 50, 640, 11, false, "15 23 42");

            metric(content, 320, 682, "Bruttoumsatz", formatMoney(payout.grossSalesAmount()));
            metric(content, 450, 682, "Auszahlung", formatMoney(payout.netPayoutAmount()));
            metric(content, 320, 624, "Provision", formatMoney(payout.commissionAmount()));
            metric(content, 450, 624, "Rückbehalt", formatMoney(payout.openReturnHoldbackAmount()));

            rect(content, 50, 560, 495, 24, "15 23 42", true);
            text(content, "Datum", 60, 569, 8.5, true, "255 255 255");
            text(content, "Typ", 128, 569, 8.5, true, "255 255 255");
            text(content, "Referenz", 190, 569, 8.5, true, "255 255 255");
            text(content, "Beschreibung", 270, 569, 8.5, true, "255 255 255");
            text(content, "Netto", 510, 569, 8.5, true, "255 255 255");

            int y = 538;
            int index = 0;
            for (SellerPayoutItemResponse item : detail.items().stream().limit(12).toList()) {
                String fill = index % 2 == 0 ? "248 250 252" : "255 255 255";
                rect(content, 50, y - 12, 495, 24, fill, true);
                text(content, formatDateTimeValue(item.occurredAt()), 60, y, 8, false, "51 65 85");
                text(content, item.type().name(), 128, y, 8, false, "51 65 85");
                text(content, blankToDash(item.referenceNumber()), 190, y, 8, false, "51 65 85");
                text(content, trim(item.description(), 34), 270, y, 8, false, "15 23 42");
                textRight(content, formatMoney(item.netAmount()), 530, y, 8, true, "15 23 42");
                y -= 24;
                index++;
            }

            if (detail.items().size() > 12) {
                text(content, "+" + (detail.items().size() - 12) + " weitere Positionen im CSV-Export", 60, y, 8.5, false, "100 116 139");
                y -= 22;
            } else if (detail.items().isEmpty()) {
                text(content, "Keine Positionen vorhanden.", 60, y, 9, false, "100 116 139");
                y -= 22;
            }

            int summaryY = 260;
            rect(content, 315, summaryY - 10, 230, 142, "248 250 252", true);
            text(content, "Zusammenfassung", 335, summaryY + 112, 13, true, "15 23 42");
            summaryRow(content, summaryY + 88, "Bruttoumsatz", payout.grossSalesAmount());
            summaryRow(content, summaryY + 70, "Rabatte", payout.discountAmount().negate());
            summaryRow(content, summaryY + 52, "Provisionen", payout.commissionAmount().negate());
            summaryRow(content, summaryY + 34, "Gebühren", payout.feeAmount().negate());
            summaryRow(content, summaryY + 16, "Retouren/Rückbehalte", payout.openReturnHoldbackAmount().add(payout.settledReturnAmount()).negate());
            summaryRow(content, summaryY - 2, "Manuelle Korrektur", payout.manualAdjustmentAmount());
            rect(content, 335, summaryY - 20, 190, 1, "15 23 42", true);
            text(content, "Auszahlung", 335, summaryY - 38, 11, true, "15 23 42");
            textRight(content, formatMoney(payout.netPayoutAmount()), 525, summaryY - 38, 11, true, "15 23 42");

            if (payout.correctionReason() != null && !payout.correctionReason().isBlank()) {
                rect(content, 50, 118, 230, 45, "255 247 237", true);
                text(content, "Korrekturgrund", 65, 143, 10, true, "154 52 18");
                text(content, trim(payout.correctionReason(), 42), 65, 128, 8.5, false, "154 52 18");
            }

            rect(content, 50, 56, 495, 1, "226 232 240", true);
            text(content, "Webshop GmbH | Marketplace Settlement | service@webshop.example", 50, 38, 8, false, "100 116 139");
            textRight(content, "Seite 1", 545, 38, 8, false, "100 116 139");

            String stream = content.toString();
            List<String> objects = List.of(
                    "<< /Type /Catalog /Pages 2 0 R >>",
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
                    "<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "endstream"
            );

            StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            for (int objectIndex = 0; objectIndex < objects.size(); objectIndex++) {
                offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                pdf.append(objectIndex + 1).append(" 0 obj\n")
                        .append(objects.get(objectIndex))
                        .append("\nendobj\n");
            }

            int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
            pdf.append("0000000000 65535 f \n");
            for (Integer offset : offsets) {
                pdf.append(String.format("%010d 00000 n \n", offset));
            }
            pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
            pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF");
            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }

        private static void metric(StringBuilder content, int x, int y, String label, String value) {
            rect(content, x, y - 34, 105, 44, "248 250 252", true);
            rect(content, x, y - 34, 105, 44, "226 232 240", false);
            text(content, label, x + 12, y - 6, 8.5, true, "100 116 139");
            text(content, value, x + 12, y - 23, 13, true, "15 23 42");
        }

        private static void summaryRow(StringBuilder content, int y, String label, BigDecimal value) {
            text(content, label, 335, y, 8.5, false, "71 85 105");
            textRight(content, formatMoney(value), 525, y, 8.5, false, "15 23 42");
        }

        private static String formatMoney(BigDecimal value) {
            return (value == null ? BigDecimal.ZERO : value)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString() + " EUR";
        }

        private static String blankToDash(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }

        private static String statusLabel(SellerPayoutStatus status) {
            return switch (status) {
                case OPEN -> "Offen";
                case IN_REVIEW -> "In Prüfung";
                case APPROVED -> "Freigegeben";
                case CORRECTED -> "Korrigiert";
                case PAID_OUT -> "Ausgezahlt";
            };
        }

        private static String statusColor(SellerPayoutStatus status) {
            return switch (status) {
                case OPEN -> "37 99 235";
                case IN_REVIEW, CORRECTED -> "217 119 6";
                case APPROVED, PAID_OUT -> "22 101 52";
            };
        }

        private static String formatDate(LocalDate value) {
            return value == null ? "-" : DateTimeFormatter.ofPattern("dd.MM.yyyy").format(value);
        }

        private static String formatDateTimeValue(Instant value) {
            return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
        }

        private static String trim(String value, int maxLength) {
            String safe = asciiSafe(value);
            return safe.length() <= maxLength ? safe : safe.substring(0, maxLength - 3) + "...";
        }

        private static void rect(StringBuilder content, int x, int y, int width, int height, String rgb, boolean fill) {
            content.append("q\n")
                    .append(pdfColor(rgb)).append(fill ? " rg\n" : " RG\n")
                    .append(x).append(' ').append(y).append(' ').append(width).append(' ').append(height)
                    .append(fill ? " re f\n" : " re S\n")
                    .append("Q\n");
        }

        private static void text(StringBuilder content, String value, int x, int y, double size, boolean bold, String rgb) {
            content.append("BT\n/")
                    .append(bold ? "F2" : "F1")
                    .append(' ').append(size).append(" Tf\n")
                    .append(pdfColor(rgb)).append(" rg\n")
                    .append(x).append(' ').append(y).append(" Td\n(")
                    .append(escape(asciiSafe(value)))
                    .append(") Tj\nET\n");
        }

        private static void textRight(StringBuilder content, String value, int x, int y, double size, boolean bold, String rgb) {
            String safe = asciiSafe(value);
            double width = safe.length() * size * 0.48;
            text(content, safe, (int) Math.round(x - width), y, size, bold, rgb);
        }

        private static String pdfColor(String rgb) {
            String[] parts = rgb.trim().split("\\s+");
            if (parts.length != 3) {
                return "0 0 0";
            }
            return formatColorPart(parts[0]) + " " + formatColorPart(parts[1]) + " " + formatColorPart(parts[2]);
        }

        private static String formatColorPart(String value) {
            double normalized = Math.max(0, Math.min(255, Double.parseDouble(value))) / 255.0;
            return String.format(Locale.ROOT, "%.3f", normalized);
        }

        private static List<String> wrap(String value, int maxLength) {
            if (value == null || value.length() <= maxLength) {
                return List.of(value == null ? "" : value);
            }
            List<String> lines = new ArrayList<>();
            String remaining = value;
            while (remaining.length() > maxLength) {
                int splitAt = remaining.lastIndexOf(' ', maxLength);
                if (splitAt < 1) {
                    splitAt = maxLength;
                }
                lines.add(remaining.substring(0, splitAt).trim());
                remaining = remaining.substring(splitAt).trim();
            }
            lines.add(remaining);
            return lines;
        }

        private static String asciiSafe(String value) {
            String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
            StringBuilder safe = new StringBuilder(normalized.length());
            for (int index = 0; index < normalized.length(); index++) {
                char character = normalized.charAt(index);
                safe.append(character <= 255 ? character : '?');
            }
            return safe.toString();
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\")
                    .replace("(", "\\(")
                    .replace(")", "\\)");
        }
    }
}
