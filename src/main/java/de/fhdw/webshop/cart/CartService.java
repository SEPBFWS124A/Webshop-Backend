package de.fhdw.webshop.cart;

import de.fhdw.webshop.cart.dto.AddToCartRequest;
import de.fhdw.webshop.cart.dto.CartItemResponse;
import de.fhdw.webshop.cart.dto.CartResponse;
import de.fhdw.webshop.cart.dto.QuickOrderConfirmItem;
import de.fhdw.webshop.cart.dto.QuickOrderConfirmRequest;
import de.fhdw.webshop.cart.dto.QuickOrderConfirmResponse;
import de.fhdw.webshop.cart.dto.QuickOrderPreviewResponse;
import de.fhdw.webshop.cart.dto.QuickOrderPreviewRow;
import de.fhdw.webshop.discount.Coupon;
import de.fhdw.webshop.discount.CouponRepository;
import de.fhdw.webshop.discount.VolumeDiscountPolicy;
import de.fhdw.webshop.discount.VolumeDiscountPolicy.VolumeDiscountResult;
import de.fhdw.webshop.order.Order;
import de.fhdw.webshop.order.OrderItem;
import de.fhdw.webshop.order.OrderItemRepository;
import de.fhdw.webshop.order.OrderRepository;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.product.ProductRepository;
import de.fhdw.webshop.product.ProductType;
import de.fhdw.webshop.product.ProductService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserType;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductService.DiscountLookupPort discountLookupPort;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CouponRepository couponRepository;

    private static final BigDecimal TAX_RATE      = BigDecimal.valueOf(0.19);
    private static final BigDecimal SHIPPING_COST = new BigDecimal("4.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    private static final long QUICK_ORDER_MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final Set<BigDecimal> GIFT_CARD_AMOUNTS = Set.of(
            new BigDecimal("15.00"),
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            new BigDecimal("100.00")
    );

    /** US #41/#43 — Returns the cart with a full price breakdown (subtotal, 19% VAT, shipping, total). */
    @Transactional
    public CartResponse getCart(Long userId) {
        return getCart(userId, null);
    }

    /** US #75 — Optional coupon preview is included in the returned totals. */
    @Transactional
    public CartResponse getCart(Long userId, String couponCode) {
        List<String> messages = normalizeCartQuantities(userId);
        List<CartItem> cartItems = cartRepository.findByUserId(userId);
        List<CartItemResponse> itemResponses = cartItems.stream()
                .map(cartItem -> toItemResponse(cartItem, userId))
                .toList();

        BigDecimal itemSubtotal = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Coupon coupon = resolveCoupon(couponCode, userId);
        GiftCardRedemption giftCardRedemption = resolveGiftCardRedemption(couponCode, coupon);
        boolean manualDiscountApplied = coupon != null || giftCardRedemption != null;
        int totalItemCount = itemResponses.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();
        VolumeDiscountResult volumeDiscount = VolumeDiscountPolicy.resolve(itemSubtotal, totalItemCount, manualDiscountApplied);
        BigDecimal discountAmount = coupon != null
                ? calculateDiscount(itemSubtotal, coupon)
                : giftCardRedemption != null
                    ? calculateGiftCardDiscount(itemSubtotal, giftCardRedemption)
                : volumeDiscount.amount();
        String discountType = resolveDiscountType(coupon, giftCardRedemption, volumeDiscount);
        String discountLabel = resolveDiscountLabel(coupon, giftCardRedemption, volumeDiscount);
        BigDecimal discountPercent = resolveDiscountPercent(coupon, giftCardRedemption, volumeDiscount);
        List<String> discountMessages = resolveDiscountMessages(volumeDiscount);
        BigDecimal subtotal = itemSubtotal.subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal tax = subtotal.multiply(TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingCost = calculateShippingCost(subtotal, itemResponses.isEmpty());

        BigDecimal total = subtotal.add(tax).add(shippingCost)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalCo2EmissionKg = itemResponses.stream()
                .map(CartItemResponse::lineCo2EmissionKg)
                .filter(lineCo2EmissionKg -> lineCo2EmissionKg != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
        int co2EmissionCoveredItemCount = itemResponses.stream()
                .filter(item -> item.co2EmissionKg() != null)
                .mapToInt(CartItemResponse::quantity)
                .sum();
        int co2EmissionTotalItemCount = itemResponses.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();

        return new CartResponse(
                itemResponses,
                subtotal,
                discountAmount,
                tax,
                shippingCost,
                total,
                totalCo2EmissionKg,
                co2EmissionCoveredItemCount,
                co2EmissionTotalItemCount,
                manualDiscountApplied ? couponCode.trim() : null,
                messages,
                discountType,
                discountLabel,
                discountPercent,
                discountMessages
        );
    }

    /** US #39 — Add an item to the cart; if already present, increase quantity. */
    @Transactional
    public CartResponse addItem(User user, AddToCartRequest addToCartRequest) {
        Product product = productService.loadProduct(addToCartRequest.productId());
        if (!product.isPurchasable() || getAvailableStock(product) <= 0) {
            throw new IllegalArgumentException("Product is not available for purchase: " + product.getId());
        }
        String personalizationText = normalizePersonalizationText(product, addToCartRequest.personalizationText());
        BigDecimal giftCardAmount = resolveGiftCardAmount(product, addToCartRequest.giftCardAmount());
        String giftCardRecipientEmail = normalizeEmail(addToCartRequest.giftCardRecipientEmail());
        String giftCardMessage = normalizeGiftCardMessage(addToCartRequest.giftCardMessage());

        CartItem cartItem = cartRepository
                .findByUserIdAndProductIdAndPersonalizationTextAndGiftCardAmountAndGiftCardRecipientEmailAndGiftCardMessage(
                        user.getId(),
                        addToCartRequest.productId(),
                        personalizationText,
                        giftCardAmount,
                        giftCardRecipientEmail,
                        giftCardMessage)
                .orElseGet(() -> {
                    CartItem newCartItem = new CartItem();
                    newCartItem.setUser(user);
                    newCartItem.setProduct(product);
                    newCartItem.setQuantity(0);
                    newCartItem.setPersonalizationText(personalizationText);
                    newCartItem.setGiftCardAmount(giftCardAmount);
                    newCartItem.setGiftCardRecipientEmail(giftCardRecipientEmail);
                    newCartItem.setGiftCardMessage(giftCardMessage);
                    return newCartItem;
                });

        cartItem.setQuantity(cartItem.getQuantity() + addToCartRequest.quantity());
        cartRepository.save(cartItem);
        return getCart(user.getId());
    }

    /** US #40 — Remove a specific product from the cart. */
    @Transactional
    public CartResponse removeItem(User user, Long productId) {
        cartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new EntityNotFoundException("Item not in cart: productId=" + productId));
        cartRepository.deleteByUserIdAndProductId(user.getId(), productId);
        return getCart(user.getId());
    }

    @Transactional
    public CartResponse removeItemByCartItemId(User user, Long cartItemId) {
        CartItem cartItem = cartRepository.findByIdAndUserId(cartItemId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Item not in cart: cartItemId=" + cartItemId));
        cartRepository.delete(cartItem);
        return getCart(user.getId());
    }

    /** US #73 — Set a new quantity; quantity 0 removes the item entirely. */
    @Transactional
    public CartResponse updateItemQuantity(User user, Long productId, int quantity) {
        if (quantity <= 0) {
            return removeItem(user, productId);
        }

        CartItem cartItem = cartRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new EntityNotFoundException("Item not in cart: productId=" + productId));
        cartItem.setQuantity(quantity);
        cartRepository.save(cartItem);
        return getCart(user.getId());
    }

    @Transactional
    public CartResponse updateItemQuantityByCartItemId(User user, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            return removeItemByCartItemId(user, cartItemId);
        }

        CartItem cartItem = cartRepository.findByIdAndUserId(cartItemId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Item not in cart: cartItemId=" + cartItemId));
        cartItem.setQuantity(quantity);
        cartRepository.save(cartItem);
        return getCart(user.getId());
    }

    /**
     * US #50 — Re-add all still-purchasable items from a past order into the cart.
     * Items whose products are no longer purchasable are silently skipped (US #49).
     */
    @Transactional
    public CartResponse reorder(User user, Long orderId) {
        Order previousOrder = orderRepository.findByIdAndCustomerId(orderId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));

        for (OrderItem orderItem : previousOrder.getItems()) {
            Product product = orderItem.getProduct();
            if (!product.isPurchasable()) {
                continue;
            }
            CartItem cartItem = cartRepository
                    .findByUserIdAndProductIdAndPersonalizationTextAndGiftCardAmountAndGiftCardRecipientEmailAndGiftCardMessage(
                            user.getId(),
                            product.getId(),
                            orderItem.getPersonalizationText(),
                            orderItem.getGiftCardAmount(),
                            orderItem.getGiftCardRecipientEmail(),
                            orderItem.getGiftCardMessage())
                    .orElseGet(() -> {
                        CartItem newCartItem = new CartItem();
                        newCartItem.setUser(user);
                        newCartItem.setProduct(product);
                        newCartItem.setQuantity(0);
                        newCartItem.setPersonalizationText(orderItem.getPersonalizationText());
                        newCartItem.setGiftCardAmount(orderItem.getGiftCardAmount());
                        newCartItem.setGiftCardRecipientEmail(orderItem.getGiftCardRecipientEmail());
                        newCartItem.setGiftCardMessage(orderItem.getGiftCardMessage());
                        return newCartItem;
                    });
            cartItem.setQuantity(cartItem.getQuantity() + orderItem.getQuantity());
            cartRepository.save(cartItem);
        }
        return getCart(user.getId());
    }

    @Transactional(readOnly = true)
    public QuickOrderPreviewResponse previewQuickOrderCsv(User user, MultipartFile file) {
        requireBusinessCustomer(user);
        validateQuickOrderFile(file);

        try {
            return previewQuickOrderItems(user, parseQuickOrderCsv(file));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Die CSV-Datei konnte nicht gelesen werden.");
        }
    }

    @Transactional
    public QuickOrderConfirmResponse confirmQuickOrder(User user, QuickOrderConfirmRequest request) {
        requireBusinessCustomer(user);
        List<QuickOrderConfirmItem> requestedItems = request == null || request.items() == null
                ? List.of()
                : request.items();
        if (requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Bitte wähle mindestens eine gültige CSV-Zeile aus.");
        }

        QuickOrderPreviewResponse preview = previewQuickOrderItems(user, requestedItems);
        List<QuickOrderPreviewRow> skippedRows = preview.rows().stream()
                .filter(row -> !row.valid())
                .toList();

        int addedLines = 0;
        int addedQuantity = 0;
        for (QuickOrderPreviewRow row : preview.rows()) {
            if (!row.valid() || row.productId() == null || row.requestedQuantity() == null) {
                continue;
            }

            Product product = productService.loadProduct(row.productId());
            CartItem cartItem = cartRepository
                    .findByUserIdAndProductIdAndPersonalizationTextAndGiftCardAmountAndGiftCardRecipientEmailAndGiftCardMessage(
                            user.getId(),
                            product.getId(),
                            null,
                            null,
                            null,
                            null)
                    .orElseGet(() -> {
                        CartItem newCartItem = new CartItem();
                        newCartItem.setUser(user);
                        newCartItem.setProduct(product);
                        newCartItem.setQuantity(0);
                        return newCartItem;
                    });
            cartItem.setQuantity(cartItem.getQuantity() + row.requestedQuantity());
            cartRepository.save(cartItem);
            addedLines++;
            addedQuantity += row.requestedQuantity();
        }

        return new QuickOrderConfirmResponse(getCart(user.getId()), addedLines, addedQuantity, skippedRows);
    }

    /** Called by OrderService after checkout to clear the cart. */
    @Transactional
    public void clearCartSilently(Long userId) {
        cartRepository.deleteByUserId(userId);
    }

    @Transactional
    public CartResponse clearCart(Long userId) {
        clearCartSilently(userId);
        return getCart(userId);
    }

    private CartItemResponse toItemResponse(CartItem cartItem, Long userId) {
        BigDecimal discountPercent = discountLookupPort.findBestActiveDiscountPercent(userId, cartItem.getProduct().getId());
        BigDecimal recommendedRetailPrice = resolveUnitPrice(cartItem.getProduct(), cartItem.getGiftCardAmount());
        BigDecimal effectiveUnitPrice;
        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal.valueOf(100)));
            effectiveUnitPrice = recommendedRetailPrice.multiply(multiplier)
                    .setScale(2, RoundingMode.HALF_UP);
        } else {
            effectiveUnitPrice = recommendedRetailPrice;
        }
        BigDecimal lineTotal = effectiveUnitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        BigDecimal co2EmissionKg = cartItem.getProduct().getCo2EmissionKg();
        BigDecimal lineCo2EmissionKg = co2EmissionKg == null
                ? null
                : co2EmissionKg.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                        .setScale(3, RoundingMode.HALF_UP);

        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getProduct().getId(),
                cartItem.getProduct().getName(),
                toVariantValues(cartItem.getProduct()),
                cartItem.getPersonalizationText(),
                cartItem.getGiftCardAmount(),
                cartItem.getGiftCardRecipientEmail(),
                cartItem.getGiftCardMessage(),
                cartItem.getProduct().getImageUrl(),
                effectiveUnitPrice,
                co2EmissionKg,
                getAvailableStock(cartItem.getProduct()),
                cartItem.getQuantity(),
                lineTotal,
                lineCo2EmissionKg,
                cartItem.getAddedAt()
        );
    }

    private List<String> normalizeCartQuantities(Long userId) {
        List<String> messages = new ArrayList<>();
        List<CartItem> cartItems = cartRepository.findByUserId(userId);

        for (CartItem cartItem : cartItems) {
            int availableStock = getAvailableStock(cartItem.getProduct());
            if (availableStock <= 0) {
                cartRepository.delete(cartItem);
                messages.add(cartItem.getProduct().getName() + " wurde entfernt, weil der Artikel nicht mehr verfügbar ist.");
                continue;
            }

            if (cartItem.getQuantity() > availableStock) {
                cartItem.setQuantity(availableStock);
                cartRepository.save(cartItem);
                messages.add(cartItem.getProduct().getName() + " wurde auf " + availableStock + " Stück angepasst.");
            }
        }

        return messages;
    }

    private Coupon resolveCoupon(String couponCode, Long userId) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        if (isGiftCardCode(couponCode)) {
            return null;
        }

        Coupon coupon = couponRepository.findByCodeAndCustomerId(couponCode.trim(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Coupon code not found: " + couponCode));

        if (coupon.isUsed()) {
            throw new IllegalArgumentException("Coupon has already been used: " + couponCode);
        }
        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Coupon has expired: " + couponCode);
        }
        return coupon;
    }

    private GiftCardRedemption resolveGiftCardRedemption(String couponCode, Coupon coupon) {
        if (couponCode == null || couponCode.isBlank() || coupon != null || !isGiftCardCode(couponCode)) {
            return null;
        }
        OrderItem giftCardItem = orderItemRepository.findByGiftCardCodeIgnoreCase(couponCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Gutscheincode nicht gefunden: " + couponCode));
        if (giftCardItem.getGiftCardAmount() == null || giftCardItem.getGiftCardCode() == null) {
            throw new IllegalArgumentException("Gutscheincode ist ungültig: " + couponCode);
        }
        if (giftCardItem.getGiftCardRedeemedAt() != null) {
            throw new IllegalArgumentException("Gutscheincode wurde bereits eingelöst: " + couponCode);
        }
        return new GiftCardRedemption(giftCardItem, giftCardItem.getGiftCardAmount());
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, Coupon coupon) {
        if (coupon == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal multiplier = coupon.getDiscountPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return subtotal.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGiftCardDiscount(BigDecimal subtotal, GiftCardRedemption giftCardRedemption) {
        if (giftCardRedemption == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return giftCardRedemption.amount().min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveDiscountType(Coupon coupon, GiftCardRedemption giftCardRedemption, VolumeDiscountResult volumeDiscount) {
        if (coupon != null) {
            return "COUPON";
        }
        if (giftCardRedemption != null) {
            return "GIFT_CARD";
        }
        return volumeDiscount.applied() ? VolumeDiscountPolicy.DISCOUNT_TYPE : null;
    }

    private String resolveDiscountLabel(Coupon coupon, GiftCardRedemption giftCardRedemption, VolumeDiscountResult volumeDiscount) {
        if (coupon != null) {
            return "Gutschein " + coupon.getCode();
        }
        if (giftCardRedemption != null) {
            return "Geschenkgutschein " + giftCardRedemption.item().getGiftCardCode();
        }
        return volumeDiscount.label();
    }

    private BigDecimal resolveDiscountPercent(Coupon coupon, GiftCardRedemption giftCardRedemption, VolumeDiscountResult volumeDiscount) {
        if (coupon != null) {
            return coupon.getDiscountPercent();
        }
        if (giftCardRedemption != null) {
            return null;
        }
        return volumeDiscount.percent();
    }

    private boolean isGiftCardCode(String couponCode) {
        return couponCode != null && couponCode.trim().toUpperCase().startsWith("GUT-");
    }

    private List<String> resolveDiscountMessages(VolumeDiscountResult volumeDiscount) {
        if (volumeDiscount.exclusionMessage() == null || volumeDiscount.exclusionMessage().isBlank()) {
            return List.of();
        }
        return List.of(volumeDiscount.exclusionMessage());
    }

    private BigDecimal calculateShippingCost(BigDecimal subtotal, boolean emptyCart) {
        if (emptyCart || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO;
        }

        return SHIPPING_COST;
    }

    private int getAvailableStock(Product product) {
        if (!product.isPurchasable()) {
            return 0;
        }
        if (product.getProductType() == ProductType.DIGITAL_GIFT_CARD) {
            return 999999;
        }
        return Math.max(product.getStock(), 0);
    }

    private void requireBusinessCustomer(User user) {
        if (user == null || user.getUserType() != UserType.BUSINESS) {
            throw new IllegalArgumentException("Die Schnellbestellung ist nur für B2B-Kunden verfügbar.");
        }
    }

    private void validateQuickOrderFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Bitte lade eine CSV-Datei hoch.");
        }
        if (file.getSize() > QUICK_ORDER_MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Die CSV-Datei darf maximal 1 MB groß sein.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".csv")) {
            throw new IllegalArgumentException("Bitte lade eine Datei im CSV-Format hoch.");
        }
    }

    private List<QuickOrderConfirmItem> parseQuickOrderCsv(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IllegalArgumentException("Die CSV-Datei enthält keine Kopfzeile.");
        }

        String header = stripBom(lines[0]);
        String delimiter = header.chars().filter(character -> character == ';').count()
                >= header.chars().filter(character -> character == ',').count()
                ? ";"
                : ",";
        String[] columns = splitCsvLine(header, delimiter);
        int articleIndex = findColumnIndex(columns, "artikelnummer", "sku");
        int quantityIndex = findColumnIndex(columns, "menge", "quantity");
        if (articleIndex < 0 || quantityIndex < 0) {
            throw new IllegalArgumentException("Die CSV-Datei braucht die Spalten Artikelnummer und Menge.");
        }

        List<QuickOrderConfirmItem> items = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] values = splitCsvLine(line, delimiter);
            String articleNumber = articleIndex < values.length ? cleanCsvValue(values[articleIndex]) : "";
            String quantity = quantityIndex < values.length ? cleanCsvValue(values[quantityIndex]) : "";
            items.add(new QuickOrderConfirmItem(index + 1, articleNumber, parseIntegerOrNull(quantity)));
        }
        return items;
    }

    private QuickOrderPreviewResponse previewQuickOrderItems(User user, List<QuickOrderConfirmItem> requestedItems) {
        Map<Long, Integer> quantityByProductId = new HashMap<>();
        cartRepository.findByUserId(user.getId()).forEach(item ->
                quantityByProductId.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum));

        List<QuickOrderPreviewRow> rows = new ArrayList<>();
        int totalRequestedQuantity = 0;
        for (QuickOrderConfirmItem item : requestedItems) {
            String articleNumber = item.articleNumber() == null ? "" : item.articleNumber().trim();
            Integer requestedQuantity = item.quantity();
            List<String> errors = new ArrayList<>();
            Product product = null;
            Integer availableStock = null;

            if (articleNumber.isBlank()) {
                errors.add("Artikelnummer fehlt.");
            } else {
                product = productRepository.findFirstBySkuIgnoreCase(articleNumber)
                        .orElse(null);
                if (product == null) {
                    errors.add("Artikelnummer wurde nicht gefunden.");
                }
            }

            if (requestedQuantity == null || requestedQuantity <= 0) {
                errors.add("Menge muss größer als 0 sein.");
            } else {
                totalRequestedQuantity += requestedQuantity;
            }

            if (product != null) {
                availableStock = getAvailableStock(product);
                int quantityAlreadyPlanned = quantityByProductId.getOrDefault(product.getId(), 0);
                int totalQuantityAfterImport = quantityAlreadyPlanned + Math.max(requestedQuantity == null ? 0 : requestedQuantity, 0);

                if (!product.isPurchasable() || availableStock <= 0) {
                    errors.add("Artikel ist aktuell nicht bestellbar.");
                } else if (product.getProductType() == ProductType.DIGITAL_GIFT_CARD) {
                    errors.add("Digitale Gutscheine können nicht per CSV-Schnellbestellung importiert werden.");
                } else if (requestedQuantity != null && requestedQuantity > 0 && totalQuantityAfterImport > availableStock) {
                    errors.add("Bestand reicht nicht aus. Verfügbar: " + availableStock
                            + ", bereits im Warenkorb/Import: " + quantityAlreadyPlanned + ".");
                }

                if (requestedQuantity != null && requestedQuantity > 0 && errors.isEmpty()) {
                    quantityByProductId.put(product.getId(), totalQuantityAfterImport);
                }
            }

            rows.add(new QuickOrderPreviewRow(
                    item.lineNumber() == null ? 0 : item.lineNumber(),
                    articleNumber,
                    requestedQuantity,
                    product == null ? null : product.getId(),
                    product == null ? null : product.getName(),
                    availableStock,
                    errors.isEmpty(),
                    errors
            ));
        }

        int validRows = (int) rows.stream().filter(QuickOrderPreviewRow::valid).count();
        return new QuickOrderPreviewResponse(rows, validRows, rows.size() - validRows, totalRequestedQuantity);
    }

    private int findColumnIndex(String[] columns, String... acceptedNames) {
        for (int index = 0; index < columns.length; index++) {
            String normalizedColumn = normalizeColumnName(columns[index]);
            for (String acceptedName : acceptedNames) {
                if (normalizedColumn.equals(acceptedName)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String normalizeColumnName(String value) {
        return cleanCsvValue(value)
                .toLowerCase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replaceAll("[^a-z0-9]", "");
    }

    private String[] splitCsvLine(String line, String delimiter) {
        String normalizedLine = cleanOuterQuotes(stripBom(line == null ? "" : line.trim()));
        return normalizedLine.split(java.util.regex.Pattern.quote(delimiter), -1);
    }

    private String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private String cleanCsvValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = stripBom(value).trim();
        trimmedValue = cleanOuterQuotes(trimmedValue);
        if (trimmedValue.startsWith("\"")) {
            trimmedValue = trimmedValue.substring(1).trim();
        }
        if (trimmedValue.endsWith("\"")) {
            trimmedValue = trimmedValue.substring(0, trimmedValue.length() - 1).trim();
        }
        return trimmedValue.replace("\"\"", "\"").trim();
    }

    private String cleanOuterQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        if (trimmedValue.length() >= 2 && trimmedValue.startsWith("\"") && trimmedValue.endsWith("\"")) {
            return trimmedValue.substring(1, trimmedValue.length() - 1).replace("\"\"", "\"").trim();
        }
        return trimmedValue;
    }

    private Integer parseIntegerOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BigDecimal resolveUnitPrice(Product product, BigDecimal giftCardAmount) {
        if (product.getProductType() == ProductType.DIGITAL_GIFT_CARD) {
            return resolveGiftCardAmount(product, giftCardAmount);
        }
        return product.getRecommendedRetailPrice();
    }

    private BigDecimal resolveGiftCardAmount(Product product, BigDecimal requestedAmount) {
        if (product.getProductType() != ProductType.DIGITAL_GIFT_CARD) {
            return null;
        }
        if (requestedAmount == null) {
            throw new IllegalArgumentException("Bitte wähle einen Gutscheinbetrag aus.");
        }
        BigDecimal normalizedAmount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (!GIFT_CARD_AMOUNTS.contains(normalizedAmount)) {
            throw new IllegalArgumentException("Der gewählte Gutscheinbetrag ist nicht verfügbar.");
        }
        return normalizedAmount;
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim();
        if (!normalizedValue.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Die Empfänger-E-Mail-Adresse ist ungültig.");
        }
        return normalizedValue;
    }

    private String normalizeGiftCardMessage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > 1000) {
            throw new IllegalArgumentException("Die persönliche Nachricht darf maximal 1000 Zeichen lang sein.");
        }
        return normalizedValue;
    }

    private String normalizePersonalizationText(Product product, String personalizationText) {
        String normalizedText = trimToNull(personalizationText);
        if (!product.isPersonalizable()) {
            if (normalizedText != null) {
                throw new IllegalArgumentException("Dieser Artikel ist nicht personalisierbar.");
            }
            return null;
        }
        if (normalizedText == null) {
            return null;
        }
        Integer maxLength = product.getPersonalizationMaxLength();
        if (maxLength != null && normalizedText.length() > maxLength) {
            throw new IllegalArgumentException("Der Wunschtext darf maximal " + maxLength + " Zeichen lang sein.");
        }
        return normalizedText;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Map<String, String> toVariantValues(Product product) {
        Map<String, String> values = new LinkedHashMap<>();
        product.getVariantOptions().stream()
                .sorted((left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()))
                .forEach(option -> values.put(option.getAttributeName(), option.getAttributeValue()));
        return values;
    }

    private record GiftCardRedemption(OrderItem item, BigDecimal amount) {}
}
