package com.atakant.emailtracker.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_tokens")
@Getter @Setter
public class OAuthToken {

    // PK is the same as users.id
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId // <-- shares PK with User.id; sets userId automatically
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String provider;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;
}

