package de.fhdw.webshop.accountlink;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.accountlink.dto.AccountLinkResponse;
import de.fhdw.webshop.accountlink.dto.TeamBudgetResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.UserRole;
import de.fhdw.webshop.user.UserType;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private final AccountLinkRepository accountLinkRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<AccountLinkResponse> listLinks(Long userId) {
        User user = loadUser(userId);
        return accountLinkRepository.findAllForUserId(user.getId()).stream()
                .map(link -> toResponse(link, user.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamBudgetResponse> listTeamBudgets(User currentUser) {
        requireBusinessCustomer(currentUser);
        return accountLinkRepository.findAllForUserId(currentUser.getId()).stream()
                .map(link -> toTeamBudgetResponse(link, currentUser.getId()))
                .toList();
    }

    @Transactional
    public TeamBudgetResponse updateTeamBudget(User currentUser, Long linkedUserId, BigDecimal maxOrderValueLimit) {
        requireBusinessCustomer(currentUser);
        if (currentUser.getId().equals(linkedUserId)) {
            throw new IllegalArgumentException("A budget limit can only be set for linked employee accounts");
        }

        Long userAId = Math.min(currentUser.getId(), linkedUserId);
        Long userBId = Math.max(currentUser.getId(), linkedUserId);
        AccountLink link = accountLinkRepository.findByUserAIdAndUserBId(userAId, userBId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account link not found for users: " + currentUser.getId() + " and " + linkedUserId));

        BigDecimal normalizedLimit = normalizeBudgetLimit(maxOrderValueLimit);
        BigDecimal previousLimit = link.getMaxOrderValueLimit();
        if (!budgetLimitsEqual(previousLimit, normalizedLimit)) {
            link.setMaxOrderValueLimit(normalizedLimit);
            AccountLink savedLink = accountLinkRepository.save(link);
            User linkedUser = linkedUser(savedLink, currentUser.getId());
            auditLogService.record(currentUser, "UPDATE_TEAM_BUDGET_LIMIT", "AccountLink", savedLink.getId(),
                    AuditInitiator.USER,
                    String.format("B2B admin %s (%d) updated budget limit for %s (%d): %s -> %s",
                            currentUser.getUsername(), currentUser.getId(),
                            linkedUser.getUsername(), linkedUser.getId(),
                            formatBudgetLimit(previousLimit), formatBudgetLimit(normalizedLimit)));
            return toTeamBudgetResponse(savedLink, currentUser.getId());
        }

        return toTeamBudgetResponse(link, currentUser.getId());
    }

    @Transactional
    public List<AccountLinkResponse> createLinks(Long userId, List<Long> linkedUserIds, User adminUser) {
        if (linkedUserIds == null || linkedUserIds.isEmpty()) {
            throw new IllegalArgumentException("At least one linked user is required");
        }

        Set<Long> uniqueTargetIds = new LinkedHashSet<>(linkedUserIds);
        if (uniqueTargetIds.size() != linkedUserIds.size()) {
            throw new IllegalArgumentException("Duplicate account links in the same request are not allowed");
        }

        User sourceUser = loadActiveUser(userId);
        for (Long linkedUserId : uniqueTargetIds) {
            createSingleLink(sourceUser, linkedUserId, adminUser);
        }

        return listLinks(sourceUser.getId());
    }

    @Transactional
    public void deleteLink(Long userId, Long linkedUserId, User adminUser) {
        Long userAId = Math.min(userId, linkedUserId);
        Long userBId = Math.max(userId, linkedUserId);

        AccountLink link = accountLinkRepository.findByUserAIdAndUserBId(userAId, userBId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account link not found for users: " + userId + " and " + linkedUserId));
        accountLinkRepository.delete(link);
        auditLogService.record(adminUser, "DELETE_ACCOUNT_LINK", "AccountLink", link.getId(),
                AuditInitiator.ADMIN, "Admin removed account link between users " + userId + " and " + linkedUserId);
    }

    @Transactional
    public void removeLinksForUser(Long userId) {
        accountLinkRepository.deleteAllForUserId(userId);
    }

    private void createSingleLink(User sourceUser, Long linkedUserId, User adminUser) {
        if (sourceUser.getId().equals(linkedUserId)) {
            throw new IllegalArgumentException("A user account cannot be linked to itself");
        }

        User targetUser = loadActiveUser(linkedUserId);
        Long userAId = Math.min(sourceUser.getId(), targetUser.getId());
        Long userBId = Math.max(sourceUser.getId(), targetUser.getId());

        if (accountLinkRepository.existsByUserAIdAndUserBId(userAId, userBId)) {
            throw new IllegalArgumentException(
                    "Account link already exists for users: " + sourceUser.getId() + " and " + targetUser.getId());
        }

        AccountLink link = new AccountLink();
        if (sourceUser.getId().equals(userAId)) {
            link.setUserA(sourceUser);
            link.setUserB(targetUser);
        } else {
            link.setUserA(targetUser);
            link.setUserB(sourceUser);
        }
        link.setSourceUser(sourceUser);
        link.setTargetUser(targetUser);

        AccountLink savedLink = accountLinkRepository.save(link);
        auditLogService.record(adminUser, "CREATE_ACCOUNT_LINK", "AccountLink", savedLink.getId(),
                AuditInitiator.ADMIN,
                "Admin linked users " + sourceUser.getId() + " and " + targetUser.getId());
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private User loadActiveUser(Long userId) {
        User user = loadUser(userId);
        if (!user.isActive()) {
            throw new IllegalArgumentException("Inactive user accounts cannot be linked: " + userId);
        }
        return user;
    }

    private void requireBusinessCustomer(User currentUser) {
        if (currentUser == null
                || currentUser.getUserType() != UserType.BUSINESS
                || !currentUser.hasRole(UserRole.CUSTOMER)) {
            throw new IllegalArgumentException("Team budgets are only available for B2B customer administrators");
        }
    }

    private BigDecimal normalizeBudgetLimit(BigDecimal limit) {
        if (limit == null || BigDecimal.ZERO.compareTo(limit) == 0) {
            return null;
        }
        if (limit.signum() < 0) {
            throw new IllegalArgumentException("Budget limit must not be negative");
        }
        return limit;
    }

    private boolean budgetLimitsEqual(BigDecimal previous, BigDecimal next) {
        if (previous == null || next == null) {
            return previous == next;
        }
        return previous.compareTo(next) == 0;
    }

    private String formatBudgetLimit(BigDecimal limit) {
        return limit == null ? "unlimited" : limit + " EUR";
    }

    private AccountLinkResponse toResponse(AccountLink link, Long sourceUserId) {
        User linkedUser = linkedUser(link, sourceUserId);
        User linkSourceUser = link.getSourceUser() != null ? link.getSourceUser() : link.getUserA();
        User linkTargetUser = link.getTargetUser() != null ? link.getTargetUser() : link.getUserB();
        return new AccountLinkResponse(
                link.getId(),
                toProfileResponse(linkedUser),
                toProfileResponse(linkSourceUser),
                toProfileResponse(linkTargetUser),
                link.getCreatedAt());
    }

    private TeamBudgetResponse toTeamBudgetResponse(AccountLink link, Long sourceUserId) {
        BigDecimal limit = link.getMaxOrderValueLimit();
        return new TeamBudgetResponse(
                link.getId(),
                toProfileResponse(linkedUser(link, sourceUserId)),
                limit,
                limit == null || BigDecimal.ZERO.compareTo(limit) == 0,
                link.getCreatedAt());
    }

    private User linkedUser(AccountLink link, Long sourceUserId) {
        return link.getUserA().getId().equals(sourceUserId) ? link.getUserB() : link.getUserA();
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles(),
                user.isActive(),
                user.getEmployeeNumber(),
                user.getUserType(),
                user.getCustomerNumber(),
                user.getAgbAcceptedAt());
    }
}
