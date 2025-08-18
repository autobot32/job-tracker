package com.atakant.emailtracker.repo;

import com.atakant.emailtracker.domain.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailRepository extends JpaRepository<Email, UUID> {
    List<Email> findByUserId(UUID userId);
}
