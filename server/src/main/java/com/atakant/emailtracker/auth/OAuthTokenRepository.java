package com.atakant.emailtracker.auth;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OAuthTokenRepository extends JpaRepository<OAuthToken, UUID> {
    Optional<OAuthToken> findByUserId(UUID userId);
}
