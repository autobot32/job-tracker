package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailRepository extends JpaRepository<Email, UUID> {

    // May be used later?
    Page<Email> findByUserIdOrderBySentAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndMessageIdHash(UUID userId, String messageIdHash);

    @Query("""
        SELECT e FROM Email e
        WHERE e.userId = :userId
        ORDER BY e.sentAt DESC NULLS LAST
    """)
    List<Email> findRecentForUser(@Param("userId") UUID userId, org.springframework.data.domain.Pageable pageable);

    void deleteByUserId(UUID userId);

}
