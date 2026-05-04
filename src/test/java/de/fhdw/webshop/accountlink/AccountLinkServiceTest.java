package de.fhdw.webshop.accountlink;

import de.fhdw.webshop.accountlink.dto.AccountLinkResponse;
import de.fhdw.webshop.accountlink.dto.TeamBudgetResponse;
import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountLinkServiceTest {

    @Test
    void listsAccountLinksWithStoredDirection() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AccountLinkService service = newService(accountLinkRepository, userRepository, mock(AuditLogService.class));
        User viewedUser = businessCustomer(2L, "target");
        User sourceUser = businessCustomer(1L, "source");
        AccountLink link = link(9L, sourceUser, viewedUser, null);
        link.setSourceUser(sourceUser);
        link.setTargetUser(viewedUser);

        when(userRepository.findById(2L)).thenReturn(Optional.of(viewedUser));
        when(accountLinkRepository.findAllForUserId(2L)).thenReturn(List.of(link));

        List<AccountLinkResponse> links = service.listLinks(2L);

        assertThat(links).hasSize(1);
        assertThat(links.getFirst().linkedUser().id()).isEqualTo(1L);
        assertThat(links.getFirst().sourceUser().id()).isEqualTo(1L);
        assertThat(links.getFirst().targetUser().id()).isEqualTo(2L);
    }

    @Test
    void createLinksStoresSourceAndTargetDirection() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AccountLinkService service = newService(accountLinkRepository, userRepository, auditLogService);
        User adminUser = businessCustomer(99L, "admin");
        User sourceUser = businessCustomer(8L, "source");
        User targetUser = businessCustomer(3L, "target");

        when(userRepository.findById(8L)).thenReturn(Optional.of(sourceUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(targetUser));
        when(accountLinkRepository.existsByUserAIdAndUserBId(3L, 8L)).thenReturn(false);
        when(accountLinkRepository.save(org.mockito.ArgumentMatchers.any(AccountLink.class)))
                .thenAnswer(invocation -> {
                    AccountLink saved = invocation.getArgument(0);
                    saved.setId(15L);
                    return saved;
                });
        when(accountLinkRepository.findAllForUserId(8L)).thenReturn(List.of());

        service.createLinks(8L, List.of(3L), adminUser);

        ArgumentCaptor<AccountLink> captor = ArgumentCaptor.forClass(AccountLink.class);
        verify(accountLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getUserA().getId()).isEqualTo(3L);
        assertThat(captor.getValue().getUserB().getId()).isEqualTo(8L);
        assertThat(captor.getValue().getSourceUser().getId()).isEqualTo(8L);
        assertThat(captor.getValue().getTargetUser().getId()).isEqualTo(3L);
    }

    @Test
    void listsTeamBudgetsForBusinessCustomer() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        AccountLinkService service = newService(accountLinkRepository, mock(UserRepository.class), mock(AuditLogService.class));
        User admin = businessCustomer(1L, "admin");
        User employee = businessCustomer(2L, "employee");
        AccountLink link = link(10L, admin, employee, new BigDecimal("250.00"));

        when(accountLinkRepository.findAllForUserId(1L)).thenReturn(List.of(link));

        List<TeamBudgetResponse> budgets = service.listTeamBudgets(admin);

        assertThat(budgets).hasSize(1);
        assertThat(budgets.getFirst().employee().id()).isEqualTo(2L);
        assertThat(budgets.getFirst().maxOrderValueLimit()).isEqualByComparingTo("250.00");
        assertThat(budgets.getFirst().unlimited()).isFalse();
    }

    @Test
    void savesTeamBudgetLimitAndWritesAuditLog() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AccountLinkService service = newService(accountLinkRepository, mock(UserRepository.class), auditLogService);
        User admin = businessCustomer(1L, "admin");
        User employee = businessCustomer(2L, "employee");
        AccountLink link = link(11L, admin, employee, null);

        when(accountLinkRepository.findByUserAIdAndUserBId(1L, 2L)).thenReturn(Optional.of(link));
        when(accountLinkRepository.save(link)).thenReturn(link);

        TeamBudgetResponse response = service.updateTeamBudget(admin, 2L, new BigDecimal("1000.00"));

        assertThat(response.maxOrderValueLimit()).isEqualByComparingTo("1000.00");
        verify(auditLogService).record(eq(admin), eq("UPDATE_TEAM_BUDGET_LIMIT"), eq("AccountLink"), eq(11L),
                eq(AuditInitiator.USER), contains("unlimited -> 1000.00 EUR"));
    }

    @Test
    void zeroBudgetLimitMeansUnlimited() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AccountLinkService service = newService(accountLinkRepository, mock(UserRepository.class), auditLogService);
        User admin = businessCustomer(1L, "admin");
        User employee = businessCustomer(2L, "employee");
        AccountLink link = link(12L, admin, employee, new BigDecimal("100.00"));

        when(accountLinkRepository.findByUserAIdAndUserBId(1L, 2L)).thenReturn(Optional.of(link));
        when(accountLinkRepository.save(link)).thenReturn(link);

        TeamBudgetResponse response = service.updateTeamBudget(admin, 2L, BigDecimal.ZERO);

        assertThat(response.maxOrderValueLimit()).isNull();
        assertThat(response.unlimited()).isTrue();
        verify(auditLogService).record(eq(admin), eq("UPDATE_TEAM_BUDGET_LIMIT"), eq("AccountLink"), eq(12L),
                eq(AuditInitiator.USER), contains("100.00 EUR -> unlimited"));
    }

    @Test
    void rejectsTeamBudgetAccessForPrivateCustomers() {
        AccountLinkService service = newService(mock(AccountLinkRepository.class), mock(UserRepository.class), mock(AuditLogService.class));
        User privateCustomer = businessCustomer(1L, "private");
        privateCustomer.setUserType(UserType.PRIVATE);

        assertThatThrownBy(() -> service.listTeamBudgets(privateCustomer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("B2B");
    }

    @Test
    void rejectsBudgetUpdatesWithoutAccountLink() {
        AccountLinkRepository accountLinkRepository = mock(AccountLinkRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        AccountLinkService service = newService(accountLinkRepository, mock(UserRepository.class), auditLogService);
        User admin = businessCustomer(1L, "admin");

        when(accountLinkRepository.findByUserAIdAndUserBId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTeamBudget(admin, 99L, BigDecimal.TEN))
                .isInstanceOf(EntityNotFoundException.class);
        verify(auditLogService, never()).record(eq(admin), eq("UPDATE_TEAM_BUDGET_LIMIT"), eq("AccountLink"), eq(99L),
                eq(AuditInitiator.USER), contains("updated budget"));
    }

    private static AccountLinkService newService(AccountLinkRepository accountLinkRepository,
                                                 UserRepository userRepository,
                                                 AuditLogService auditLogService) {
        return new AccountLinkService(accountLinkRepository, userRepository, auditLogService);
    }

    private static AccountLink link(Long id, User userA, User userB, BigDecimal limit) {
        AccountLink link = new AccountLink();
        link.setId(id);
        link.setUserA(userA);
        link.setUserB(userB);
        link.setMaxOrderValueLimit(limit);
        return link;
    }

    private static User businessCustomer(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPasswordHash("hash");
        user.setUserType(UserType.BUSINESS);
        user.getRoles().add(UserRole.CUSTOMER);
        user.setActive(true);
        return user;
    }
}
