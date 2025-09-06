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

    void deleteByUserId(UUID userId);

    Optional<Email> findByGmailId(String gmailId);

}
