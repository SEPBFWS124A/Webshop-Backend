package de.fhdw.webshop.cartreminder;

import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartReminderService {

    private final CartReminderRepository cartReminderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${cart-reminders.delay-hours:24}")
    private long reminderDelayHours;

    @Transactional
    public void markCartChanged(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        CartReminder reminder = cartReminderRepository.findByUserId(userId)
                .orElseGet(() -> {
                    CartReminder newReminder = new CartReminder();
                    newReminder.setUser(user);
                    return newReminder;
                });
        Instant now = Instant.now();
        reminder.setLastModifiedAt(now);
        reminder.setReminderSentAt(null);
        reminder.setUpdatedAt(now);
        cartReminderRepository.save(reminder);
    }

    @Transactional
    public void markCartCleared(Long userId) {
        cartReminderRepository.findByUserId(userId).ifPresent(reminder -> {
            Instant now = Instant.now();
            reminder.setLastModifiedAt(null);
            reminder.setReminderSentAt(null);
            reminder.setUpdatedAt(now);
            cartReminderRepository.save(reminder);
        });
    }

    @Transactional
    public int sendDueReminders() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(reminderDelayHours));
        int sentCount = 0;

        for (CartReminder reminder : cartReminderRepository.findDueReminders(cutoff)) {
            if (sendReminderIfStillRelevant(reminder)) {
                sentCount++;
            }
        }

        return sentCount;
    }

    private boolean sendReminderIfStillRelevant(CartReminder reminder) {
        User customer = reminder.getUser();
        List<CartItem> purchasableItems = cartRepository.findByUserId(customer.getId()).stream()
                .filter(item -> item.getQuantity() > 0)
                .filter(item -> item.getProduct() != null && item.getProduct().isPurchasable())
                .toList();

        if (purchasableItems.isEmpty()) {
            markCartCleared(customer.getId());
            return false;
        }

        boolean sent = emailService.sendEmailToCustomer(
                customer,
                "Dein Warenkorb wartet auf dich",
                buildReminderBody(customer, purchasableItems)
        );

        if (!sent) {
            log.warn("Cart reminder email could not be sent for user {}", customer.getId());
            return false;
        }

        Instant now = Instant.now();
        reminder.setReminderSentAt(now);
        reminder.setUpdatedAt(now);
        cartReminderRepository.save(reminder);
        return true;
    }

    private String buildReminderBody(User customer, List<CartItem> items) {
        int totalQuantity = items.stream().mapToInt(CartItem::getQuantity).sum();
        BigDecimal total = items.stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        StringBuilder body = new StringBuilder();
        body.append("Hallo ").append(customer.getUsername()).append(",\n\n");
        body.append("dein Warenkorb wartet noch auf dich. Du hast ")
                .append(totalQuantity)
                .append(totalQuantity == 1 ? " Artikel" : " Artikel")
                .append(" im Warenkorb.\n\n");
        body.append("Warenkorb-Übersicht:\n");
        for (CartItem item : items) {
            body.append("- ")
                    .append(item.getQuantity())
                    .append("x ")
                    .append(item.getProduct().getName())
                    .append(" (")
                    .append(formatMoney(lineTotal(item)))
                    .append(")\n");
        }
        body.append("\nGesamtbetrag: ").append(formatMoney(total)).append("\n");
        body.append("Direkt zurück zum Warenkorb: ").append(cartUrl()).append("\n\n");
        body.append("Wenn du keine Warenkorb-Erinnerungen mehr erhalten möchtest, kannst du sie in deinem Profil deaktivieren.");
        return body.toString();
    }

    private BigDecimal lineTotal(CartItem item) {
        return unitPrice(item)
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal unitPrice(CartItem item) {
        if (item.getPriceOverride() != null) {
            return item.getPriceOverride();
        }
        if (item.getGiftCardAmount() != null) {
            return item.getGiftCardAmount();
        }
        Product product = item.getProduct();
        return product.getRecommendedRetailPrice() != null ? product.getRecommendedRetailPrice() : BigDecimal.ZERO;
    }

    private String cartUrl() {
        return frontendUrl.replaceAll("/+$", "") + "/cart";
    }

    private String formatMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " EUR";
    }
}
