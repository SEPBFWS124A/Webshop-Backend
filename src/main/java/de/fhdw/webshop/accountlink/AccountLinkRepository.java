package de.fhdw.webshop.accountlink;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountLinkRepository extends JpaRepository<AccountLink, Long> {

    @Query("""
            SELECT link FROM AccountLink link
            WHERE link.userA.id = :userId OR link.userB.id = :userId
            ORDER BY link.createdAt DESC
            """)
    List<AccountLink> findAllForUserId(@Param("userId") Long userId);

    Optional<AccountLink> findByUserAIdAndUserBId(Long userAId, Long userBId);

    boolean existsByUserAIdAndUserBId(Long userAId, Long userBId);

    @Modifying
    @Query("""
            DELETE FROM AccountLink link
            WHERE link.userA.id = :userId OR link.userB.id = :userId
            """)
    void deleteAllForUserId(@Param("userId") Long userId);
}
