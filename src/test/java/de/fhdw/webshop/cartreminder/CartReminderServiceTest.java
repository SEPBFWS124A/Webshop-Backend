package de.fhdw.webshop.cartreminder;

import de.fhdw.webshop.cart.CartItem;
import de.fhdw.webshop.cart.CartRepository;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.product.Product;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartReminderServiceTest {

    @Test
    void sendsReminderForDuePurchasableCartAndStoresSentTimestamp() {
        TestContext context = newContext();
        User customer = customer(1L);
        CartReminder reminder = reminder(customer);
        CartItem item = cartItem(customer, "Laptop Pro", "1299.00", 1, true);

        when(context.cartReminderRepository.findDueReminders(any(Instant.class))).thenReturn(List.of(reminder));
        when(context.cartRepository.findByUserId(customer.getId())).thenReturn(List.of(item));
        when(context.emailService.sendEmailToCustomer(any(User.class), contains("Warenkorb"), contains("Laptop Pro")))
                .thenReturn(true);

        int sent = context.service.sendDueReminders();

        assertThat(sent).isEqualTo(1);
        verify(context.emailService).sendEmailToCustomer(customer, "Dein Warenkorb wartet auf dich", 
                "Hallo kunde,\n\n"
                        + "dein Warenkorb wartet noch auf dich. Du hast 1 Artikel im Warenkorb.\n\n"
                        + "Warenkorb-Übersicht:\n"
                        + "- 1x Laptop Pro (1299.00 EUR)\n\n"
                        + "Gesamtbetrag: 1299.00 EUR\n"
                        + "Direkt zurück zum Warenkorb: http://localhost:5173/cart\n\n"
                        + "Wenn du keine Warenkorb-Erinnerungen mehr erhalten möchtest, kannst du sie in deinem Profil deaktivieren.");
        ArgumentCaptor<CartReminder> reminderCaptor = ArgumentCaptor.forClass(CartReminder.class);
        verify(context.cartReminderRepository).save(reminderCaptor.capture());
        assertThat(reminderCaptor.getValue().getReminderSentAt()).isNotNull();
    }

    @Test
    void doesNotSendReminderForEmptyOrUnavailableCartAndClearsCycle() {
        TestContext context = newContext();
        User customer = customer(2L);
        CartReminder reminder = reminder(customer);
        CartItem unavailableItem = cartItem(customer, "Archivprodukt", "19.99", 1, false);

        when(context.cartReminderRepository.findDueReminders(any(Instant.class))).thenReturn(List.of(reminder));
        when(context.cartReminderRepository.findByUserId(customer.getId())).thenReturn(Optional.of(reminder));
        when(context.cartRepository.findByUserId(customer.getId())).thenReturn(List.of(unavailableItem));

        int sent = context.service.sendDueReminders();

        assertThat(sent).isZero();
        verify(context.emailService, never()).sendEmailToCustomer(any(), any(), any());
        ArgumentCaptor<CartReminder> reminderCaptor = ArgumentCaptor.forClass(CartReminder.class);
        verify(context.cartReminderRepository).save(reminderCaptor.capture());
        assertThat(reminderCaptor.getValue().getLastModifiedAt()).isNull();
        assertThat(reminderCaptor.getValue().getReminderSentAt()).isNull();
    }

    @Test
    void cartChangeStartsNewCycleAndAllowsAnotherReminder() {
        TestContext context = newContext();
        User customer = customer(3L);
        CartReminder existingReminder = reminder(customer);
        existingReminder.setReminderSentAt(Instant.now());

        when(context.userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(context.cartReminderRepository.findByUserId(customer.getId())).thenReturn(Optional.of(existingReminder));

        context.service.markCartChanged(customer.getId());

        ArgumentCaptor<CartReminder> reminderCaptor = ArgumentCaptor.forClass(CartReminder.class);
        verify(context.cartReminderRepository).save(reminderCaptor.capture());
        assertThat(reminderCaptor.getValue().getLastModifiedAt()).isNotNull();
        assertThat(reminderCaptor.getValue().getReminderSentAt()).isNull();
    }

    private static TestContext newContext() {
        CartReminderRepository cartReminderRepository = mock(CartReminderRepository.class);
        CartRepository cartRepository = mock(CartRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        EmailService emailService = mock(EmailService.class);
        CartReminderService service = new CartReminderService(
                cartReminderRepository,
                cartRepository,
                userRepository,
                emailService
        );
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "reminderDelayHours", 24L);
        return new TestContext(service, cartReminderRepository, cartRepository, userRepository, emailService);
    }

    private static User customer(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("kunde");
        user.setEmail("kunde@example.test");
        user.setCartReminderEnabled(true);
        user.setActive(true);
        return user;
    }

    private static CartReminder reminder(User user) {
        CartReminder reminder = new CartReminder();
        reminder.setUser(user);
        reminder.setLastModifiedAt(Instant.now().minusSeconds(90_000));
        return reminder;
    }

    private static CartItem cartItem(User user, String productName, String price, int quantity, boolean purchasable) {
        Product product = new Product();
        product.setId(10L);
        product.setName(productName);
        product.setRecommendedRetailPrice(new BigDecimal(price));
        product.setPurchasable(purchasable);

        CartItem item = new CartItem();
        item.setUser(user);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private record TestContext(
            CartReminderService service,
            CartReminderRepository cartReminderRepository,
            CartRepository cartRepository,
            UserRepository userRepository,
            EmailService emailService
    ) {}
}
