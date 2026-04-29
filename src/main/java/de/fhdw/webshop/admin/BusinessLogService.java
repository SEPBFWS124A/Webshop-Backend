package de.fhdw.webshop.admin;

import de.fhdw.webshop.admin.dto.BusinessLogEntryResponse;
import de.fhdw.webshop.user.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BusinessLogService {

    private final AuditLogRepository auditLogRepository;

    public List<BusinessLogEntryResponse> search(LocalDate from, LocalDate to, String userFilter, String actionFilter) {
        Instant fromInclusive = from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return auditLogRepository.findAll(
                        buildSpecification(fromInclusive, toExclusive, normalize(userFilter), normalize(actionFilter)),
                        Sort.by(Sort.Direction.DESC, "timestamp"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Specification<AuditLog> buildSpecification(
            Instant fromInclusive,
            Instant toExclusive,
            String userFilter,
            String actionFilter) {
        return (root, query, criteriaBuilder) -> {
            if (query != null && query.getResultType() != Long.class) {
                root.fetch("user", JoinType.LEFT);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (fromInclusive != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("timestamp"), fromInclusive));
            }
            if (toExclusive != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("timestamp"), toExclusive));
            }
            if (actionFilter != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("action")),
                        contains(actionFilter)));
            }
            if (userFilter != null) {
                Join<AuditLog, User> user = root.join("user", JoinType.LEFT);
                List<Predicate> userPredicates = new ArrayList<>();
                userPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(user.get("username")), contains(userFilter)));
                userPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(user.get("email")), contains(userFilter)));
                parseLong(userFilter).ifPresent(userId -> userPredicates.add(criteriaBuilder.equal(user.get("id"), userId)));
                predicates.add(criteriaBuilder.or(userPredicates.toArray(Predicate[]::new)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private BusinessLogEntryResponse toResponse(AuditLog auditLog) {
        User user = auditLog.getUser();
        return new BusinessLogEntryResponse(
                auditLog.getId(),
                auditLog.getTimestamp(),
                user == null ? null : user.getId(),
                user == null ? "System" : user.getUsername(),
                user == null ? null : user.getEmail(),
                auditLog.getAction(),
                auditLog.getEntityType(),
                auditLog.getEntityId(),
                auditLog.getInitiatedBy(),
                auditLog.getDetails());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String contains(String value) {
        return "%" + value + "%";
    }

    private static java.util.Optional<Long> parseLong(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}
