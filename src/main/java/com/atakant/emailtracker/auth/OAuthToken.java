package com.atakant.emailtracker.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_tokens")
@Getter
@Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OAuthToken {

    // PK == FK -> users.id (as per your schema)
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String provider; // "google"

    @Column(name = "access_token", nullable = false, length = 4000)
    private String accessToken;

    @Column(name = "refresh_token", length = 4000)
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;
}

