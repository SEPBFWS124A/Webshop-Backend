package de.fhdw.webshop.accountlink;

import de.fhdw.webshop.admin.AuditInitiator;
import de.fhdw.webshop.admin.AuditLogService;
import de.fhdw.webshop.accountlink.dto.AccountLinkResponse;
import de.fhdw.webshop.user.User;
import de.fhdw.webshop.user.UserRepository;
import de.fhdw.webshop.user.dto.UserProfileResponse;
import jakarta.persistence.EntityNotFoundException;
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

    private AccountLinkResponse toResponse(AccountLink link, Long sourceUserId) {
        User linkedUser = link.getUserA().getId().equals(sourceUserId) ? link.getUserB() : link.getUserA();
        return new AccountLinkResponse(
                link.getId(),
                toProfileResponse(linkedUser),
                link.getCreatedAt());
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getUserType(),
                user.getCustomerNumber());
    }
}
