package de.fhdw.webshop.admin;

import de.fhdw.webshop.accountlink.AccountLinkService;
import de.fhdw.webshop.alerting.BusinessEmailService;
import de.fhdw.webshop.admin.dto.CreateAdminUserRequest;
import de.fhdw.webshop.auth.JwtTokenProvider;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private BusinessEmailService businessEmailService;

    @Mock
    private AccountLinkService accountLinkService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(
                userRepository,
                auditLogRepository,
                auditLogService,
                jwtTokenProvider,
                businessEmailService,
                accountLinkService,
                passwordEncoder,
                jdbcTemplate
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listAllUsersUsesActiveOnlyTrueByDefaultAndIncludesEmployeeNumber() throws Exception {
        User employee = user(
                1L,
                "max.mustermann",
                "max@example.com",
                Set.of(UserRole.EMPLOYEE),
                UserType.INTERNAL,
                true,
                null,
                "MA-10001"
        );
        when(userRepository.findAllUsers("", true)).thenReturn(List.of(employee));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("max.mustermann"))
                .andExpect(jsonPath("$[0].email").value("max@example.com"))
                .andExpect(jsonPath("$[0].roles[0]").value("EMPLOYEE"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].employeeNumber").value("MA-10001"));

        verify(userRepository).findAllUsers("", true);
    }

    @Test
    void listAllUsersFiltersInternalUsersByRoleSet() throws Exception {
        User employee = user(
                1L,
                "internal.user",
                "internal@example.com",
                Set.of(UserRole.SALES_EMPLOYEE),
                UserType.INTERNAL,
                true,
                null,
                "MA-10002"
        );
        User customer = user(
                2L,
                "customer.user",
                "customer@example.com",
                Set.of(UserRole.CUSTOMER),
                UserType.PRIVATE,
                true,
                "100123",
                null
        );
        when(userRepository.findAllUsers("", false)).thenReturn(List.of(employee, customer));

        mockMvc.perform(get("/api/admin/users")
                        .param("userType", "INTERNAL")
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("internal.user"))
                .andExpect(jsonPath("$[0].roles[0]").value("SALES_EMPLOYEE"))
                .andExpect(jsonPath("$[0].employeeNumber").value("MA-10002"));

        verify(userRepository).findAllUsers("", false);
    }

    @Test
    void listAllUsersFiltersCustomersAndKeepsInactiveUsersWhenRequested() throws Exception {
        User employee = user(
                1L,
                "internal.user",
                "internal@example.com",
                Set.of(UserRole.ADMIN),
                UserType.INTERNAL,
                true,
                null,
                "MA-10003"
        );
        User activeCustomer = user(
                2L,
                "active.customer",
                "active.customer@example.com",
                Set.of(UserRole.CUSTOMER),
                UserType.BUSINESS,
                true,
                "100124",
                null
        );
        User inactiveCustomer = user(
                3L,
                "inactive.customer",
                "inactive.customer@example.com",
                Set.of(UserRole.CUSTOMER),
                UserType.PRIVATE,
                false,
                "100125",
                null
        );
        when(userRepository.findAllUsers("", false)).thenReturn(List.of(employee, activeCustomer, inactiveCustomer));

        mockMvc.perform(get("/api/admin/users")
                        .param("userType", "CUSTOMER")
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].username").value("active.customer"))
                .andExpect(jsonPath("$[0].employeeNumber").isEmpty())
                .andExpect(jsonPath("$[1].username").value("inactive.customer"))
                .andExpect(jsonPath("$[1].employeeNumber").isEmpty())
                .andExpect(jsonPath("$[1].active").value(false));

        verify(userRepository).findAllUsers("", false);
    }

    @Test
    void createUserAssignsEmployeeNumberForInternalUsers() {
        User adminUser = user(
                99L,
                "admin",
                "admin@example.com",
                Set.of(UserRole.ADMIN),
                UserType.INTERNAL,
                true,
                null,
                "MA-99999"
        );
        CreateAdminUserRequest request = new CreateAdminUserRequest(
                "warehouse.user",
                "warehouse@example.com",
                "Secret123!",
                UserType.INTERNAL,
                UserRole.WAREHOUSE_EMPLOYEE
        );

        when(userRepository.existsByUsername("warehouse.user")).thenReturn(false);
        when(userRepository.existsByEmail("warehouse@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded-password");
        when(jdbcTemplate.queryForObject("SELECT nextval('employee_number_sequence')", Long.class)).thenReturn(10001L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0, User.class);
            savedUser.setId(123L);
            return savedUser;
        });

        var response = new AdminController(
                userRepository,
                auditLogRepository,
                auditLogService,
                jwtTokenProvider,
                businessEmailService,
                accountLinkService,
                passwordEncoder,
                jdbcTemplate
        ).createUser(request, adminUser);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().employeeNumber()).isEqualTo("MA-10001");
        assertThat(response.getBody().customerNumber()).isNull();
        assertThat(response.getBody().roles()).containsExactly(UserRole.WAREHOUSE_EMPLOYEE);
    }

    private User user(
            Long id,
            String username,
            String email,
            Set<UserRole> roles,
            UserType userType,
            boolean active,
            String customerNumber,
            String employeeNumber
    ) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("secret");
        user.setRoles(roles);
        user.setUserType(userType);
        user.setActive(active);
        user.setCustomerNumber(customerNumber);
        user.setEmployeeNumber(employeeNumber);
        return user;
    }
}

