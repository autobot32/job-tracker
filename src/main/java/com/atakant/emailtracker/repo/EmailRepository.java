package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {

    // existing
    Page<Email> findByUserIdOrderBySentAtDesc(UUID userId, Pageable pageable);

    boolean existsByUserIdAndMessageIdHash(UUID userId, String messageIdHash);

    Optional<Email> findByUserIdAndMessageIdHash(UUID userId, String messageIdHash);

}
