package de.fhdw.webshop.purchaseorder;

import de.fhdw.webshop.chat.OllamaClient;
import de.fhdw.webshop.chat.dto.ConversationEntry;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.purchaseorder.dto.CreatePurchaseOrderRequest;
import de.fhdw.webshop.purchaseorder.dto.PurchaseOrderResponse;
import de.fhdw.webshop.stockforecast.SoldQuantityRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.warehouse.WarehouseLocation;
import de.fhdw.webshop.warehouse.WarehouseLocationRepository;
import de.fhdw.webshop.warehouse.WarehouseProductStock;
import de.fhdw.webshop.warehouse.WarehouseProductStockRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private static final int HISTORY_DAYS = 30;
    private static final int TARGET_COVERAGE_DAYS = 60;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final SoldQuantityRepository soldQuantityRepository;
    private final OllamaClient ollamaClient;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseProductStockRepository warehouseProductStockRepository;

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> listAll() {
        return purchaseOrderRepository.findAllByOrderByOrderedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request, User actingUser) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + request.productId()));

        int aiSuggestedQuantity = suggestQuantity(product);

        WarehouseLocation warehouse = null;
        if (request.warehouseLocationId() != null) {
            warehouse = warehouseLocationRepository.findById(request.warehouseLocationId())
                    .orElse(null);
        }

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setProduct(product);
        purchaseOrder.setQuantity(request.quantity());
        purchaseOrder.setSupplierName(
                request.supplierName() != null && !request.supplierName().isBlank()
                        ? request.supplierName()
                        : product.getSupplierName()
        );
        purchaseOrder.setAiSuggestedQuantity(aiSuggestedQuantity);
        purchaseOrder.setOrderedByUser(actingUser);
        purchaseOrder.setWarehouseLocation(warehouse);

        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    /**
     * #138 — Confirm goods receipt.
     * Updates both the global product.stock and the warehouse-specific
     * warehouse_product_stocks entry so the AdminInventoryPage reflects the change.
     */
    @Transactional
    public PurchaseOrderResponse confirmReceipt(Long purchaseOrderId, User actingUser) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new EntityNotFoundException("Purchase order not found: " + purchaseOrderId));

        if (purchaseOrder.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new IllegalStateException("Wareneingang wurde bereits bestätigt.");
        }

        int qty = purchaseOrder.getQuantity();
        Product product = productRepository.findById(purchaseOrder.getProduct().getId())
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        // 1. Update global stock on the product
        product.setStock(product.getStock() + qty);
        productRepository.save(product);

        // 2. Update warehouse-specific stock so AdminInventoryPage reflects the change
        WarehouseLocation warehouse = purchaseOrder.getWarehouseLocation();
        if (warehouse == null) {
            // Fallback: use the first available warehouse location
            warehouse = warehouseLocationRepository.findAll().stream().findFirst().orElse(null);
        }
        if (warehouse != null) {
            final WarehouseLocation wh = warehouse;
            WarehouseProductStock stock = warehouseProductStockRepository
                    .findByProductIdAndWarehouseLocationId(product.getId(), wh.getId())
                    .orElseGet(() -> {
                        WarehouseProductStock newStock = new WarehouseProductStock();
                        newStock.setProduct(product);
                        newStock.setWarehouseLocation(wh);
                        newStock.setQuantity(0);
                        return newStock;
                    });
            stock.setQuantity(stock.getQuantity() + qty);
            warehouseProductStockRepository.save(stock);
            log.info("Received {} units of product {} into warehouse {}", qty, product.getId(), wh.getId());
        } else {
            log.warn("No warehouse location available for purchase order {}; only global stock updated", purchaseOrderId);
        }

        purchaseOrder.setStatus(PurchaseOrderStatus.RECEIVED);
        purchaseOrder.setReceivedAt(Instant.now());

        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    /** #124 — Calculate AI-suggested reorder quantity using sales history and Ollama. */
    public int suggestQuantityForProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        return suggestQuantity(product);
    }

    private int suggestQuantity(Product product) {
        Instant since = Instant.now().minus(HISTORY_DAYS, ChronoUnit.DAYS);
        int soldInPeriod = soldQuantityRepository.sumSoldQuantityForProduct(product.getId(), since);
        double avgDailySales = (double) soldInPeriod / HISTORY_DAYS;

        int leadTimeDays = product.getSupplierLeadTimeDays() != null ? product.getSupplierLeadTimeDays() : 7;
        int simpleEstimate = (int) Math.ceil(avgDailySales * (leadTimeDays + TARGET_COVERAGE_DAYS));
        simpleEstimate = Math.max(simpleEstimate, 10);

        try {
            String systemPrompt = """
                    Du bist ein Einkaufsexperte. Berechne die optimale Nachbestellmenge für ein Produkt.
                    Antworte NUR mit einer ganzen Zahl (keine Erklärung, kein Text drumherum).
                    """;
            String userPrompt = String.format(
                    "Produkt: %s | Aktueller Bestand: %d | Tagesdurchsatz (letzte 30 Tage): %.2f Stück/Tag | Lieferzeit: %d Tage | Zielreichweite: %d Tage. Wie viele Stück sollten nachbestellt werden?",
                    product.getName(), product.getStock(), avgDailySales, leadTimeDays, TARGET_COVERAGE_DAYS
            );
            String reply = ollamaClient.chat(systemPrompt,
                    List.of(new ConversationEntry("user", userPrompt)));

            String cleaned = reply.replaceAll("[^0-9]", "").trim();
            if (!cleaned.isBlank()) {
                int aiQty = Integer.parseInt(cleaned);
                if (aiQty > 0 && aiQty <= 100_000) {
                    return aiQty;
                }
            }
        } catch (Exception e) {
            log.warn("AI quantity suggestion failed for product {}, using simple estimate: {}", product.getId(), e.getMessage());
        }

        return simpleEstimate;
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder po) {
        WarehouseLocation wh = po.getWarehouseLocation();
        return new PurchaseOrderResponse(
                po.getId(),
                po.getProduct().getId(),
                po.getProduct().getName(),
                po.getProduct().getSku(),
                po.getQuantity(),
                po.getSupplierName(),
                po.getStatus(),
                po.getAiSuggestedQuantity(),
                po.getOrderedAt(),
                po.getReceivedAt(),
                wh != null ? wh.getId() : null,
                wh != null ? wh.getName() : null
        );
    }
}
