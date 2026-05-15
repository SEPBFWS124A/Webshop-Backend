package de.fhdw.webshop.customer;

import de.fhdw.webshop.cart.CartService;
import de.fhdw.webshop.cart.audit.CartChangeLogService;
import de.fhdw.webshop.config.GlobalExceptionHandler;
import de.fhdw.webshop.discount.DiscountService;
import de.fhdw.webshop.notification.EmailService;
import de.fhdw.webshop.order.OrderService;
import de.fhdw.webshop.user.BusinessInfoRepository;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserService;
import de.fhdw.webshop.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private CartService cartService;

    @Mock
    private CartChangeLogService cartChangeLogService;

    @Mock
    private OrderService orderService;

    @Mock
    private DiscountService discountService;

    @Mock
    private BusinessInfoRepository businessInfoRepository;

    @Mock
    private StatisticsService statisticsService;

    @Mock
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomerController controller = new CustomerController(
                userRepository,
                userService,
                cartService,
                cartChangeLogService,
                orderService,
                discountService,
                businessInfoRepository,
                statisticsService,
                emailService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listCustomersReturnsOnlyActiveCustomersByDefault() throws Exception {
        User activeCustomer = customer(5L, "max", "max@example.com", true, "K-100005");
        when(userRepository.findCustomers("", false, UserRole.CUSTOMER)).thenReturn(List.of(activeCustomer));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].username").value("max"))
                .andExpect(jsonPath("$[0].email").value("max@example.com"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].customerNumber").value("K-100005"));

        verify(userRepository).findCustomers("", false, UserRole.CUSTOMER);
    }

    @Test
    void listCustomersIncludesInactiveCustomersWhenRequested() throws Exception {
        User activeCustomer = customer(5L, "max", "max@example.com", true, "K-100005");
        User inactiveCustomer = customer(6L, "eva", "eva@example.com", false, "K-100006");
        when(userRepository.findCustomers("", true, UserRole.CUSTOMER))
                .thenReturn(List.of(activeCustomer, inactiveCustomer));

        mockMvc.perform(get("/api/customers").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].customerNumber").value("K-100006"));

        verify(userRepository).findCustomers("", true, UserRole.CUSTOMER);
    }

    @Test
    void deactivateCustomerUpdatesOnlyCustomerAccounts() throws Exception {
        User customer = customer(5L, "max", "max@example.com", true, "K-100005");
        when(userService.loadById(5L)).thenReturn(customer);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        mockMvc.perform(patch("/api/customers/5/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.customerNumber").value("K-100005"));

        verify(userRepository).save(customer);
    }

    @Test
    void activateCustomerReactivatesCustomerAccounts() throws Exception {
        User customer = customer(5L, "max", "max@example.com", false, "K-100005");
        when(userService.loadById(5L)).thenReturn(customer);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0, User.class));

        mockMvc.perform(patch("/api/customers/5/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.customerNumber").value("K-100005"));

        verify(userRepository).save(customer);
    }

    @Test
    void deactivateCustomerRejectsInternalAccounts() throws Exception {
        User employee = internalUser(9L, "employee", "employee@example.com", true, "MA-10001");
        when(userService.loadById(9L)).thenReturn(employee);

        mockMvc.perform(patch("/api/customers/9/deactivate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dieser Endpunkt gilt nur fuer Kundenkonten."));

        verify(userRepository, never()).save(any(User.class));
    }

    private User customer(Long id, String username, String email, boolean active, String customerNumber) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("secret");
        user.setRoles(Set.of(UserRole.CUSTOMER));
        user.setUserType(UserType.PRIVATE);
        user.setActive(active);
        user.setCustomerNumber(customerNumber);
        user.setEmployeeNumber(null);
        return user;
    }

    private User internalUser(Long id, String username, String email, boolean active, String employeeNumber) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("secret");
        user.setRoles(Set.of(UserRole.EMPLOYEE));
        user.setUserType(UserType.INTERNAL);
        user.setActive(active);
        user.setCustomerNumber(null);
        user.setEmployeeNumber(employeeNumber);
        return user;
    }
}

