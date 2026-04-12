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
     * Pass "" to skip the search filter — IS NULL on Strings infers bytea in PostgreSQL.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.role = 'CUSTOMER'
              AND u.active = true
              AND (:searchTerm = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :searchTerm, '%')))
            """)
    List<User> findActiveCustomers(@Param("searchTerm") String searchTerm);

    /**
     * Returns all users, optionally filtered by username or email substring (admin view).
     * Pass "" to skip the search filter — IS NULL on Strings infers bytea in PostgreSQL.
     */
    @Query("""
            SELECT u FROM User u
            WHERE :searchTerm = ''
               OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
               OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            """)
    List<User> findAllUsers(@Param("searchTerm") String searchTerm);

    Optional<User> findByCustomerNumber(String customerNumber);
}
