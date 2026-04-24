package de.fhdw.webshop.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

        Optional<User> findByUsername(String username);

        Optional<User> findByUsernameAndActiveTrue(String username);

        Optional<User> findByEmail(String email);

        boolean existsByUsernameAndActiveTrue(String username);

        boolean existsByEmailAndActiveTrue(String email);

        /**
         * Returns all active customers, optionally filtered by username or email substring.
         * Pass "" to skip the search filter.
         */
        @Query("""
                        SELECT u FROM User u
                        WHERE :customerRole MEMBER OF u.roles
                          AND u.active = true
                          AND (:searchTerm = ''
                               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :searchTerm, '%')))
                        """)
        List<User> findActiveCustomers(@Param("searchTerm") String searchTerm,
                                       @Param("customerRole") UserRole customerRole);

        /**
         * Returns all users, optionally filtered by username or email substring (admin view).
         * Pass "" to skip the search filter.
         */
        @Query("""
                        SELECT u FROM User u
                        WHERE (:activeOnly = false OR u.active = true)
                          AND (:searchTerm = ''
                               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                               OR LOWER(COALESCE(u.customerNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')))
                        """)
        List<User> findAllUsers(@Param("searchTerm") String searchTerm, @Param("activeOnly") boolean activeOnly);

        Optional<User> findByCustomerNumber(String customerNumber);

        /**
         * US #90 — Alle aktiven Benutzer mit einer bestimmten Rolle (z.B. für E-Mail-Digest).
         */
        @Query("""
                        SELECT u FROM User u
                        WHERE :role MEMBER OF u.roles
                          AND u.active = true
                        """)
        List<User> findByRoleAndActiveTrue(@Param("role") UserRole role);
}
