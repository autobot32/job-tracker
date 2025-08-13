package com.atakant.emailtracker.security;

import com.atakant.emailtracker.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        // We expect OIDC with Google
        DefaultOidcUser principal = (DefaultOidcUser) authentication.getPrincipal();
        String email = principal.getEmail();
        String provider = "google";

        // Upsert user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        User.builder().email(email).provider(provider).build()
                ));

        // Grab tokens from Spring’s client service
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google", authentication.getName());

        String accessToken = client.getAccessToken().getTokenValue();
        Instant expiresAt  = client.getAccessToken().getExpiresAt();
        String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;

        // Upsert tokens
        Optional<OAuthToken> existing = tokenRepository.findByUserId(user.getId());
        OAuthToken toSave = existing.orElseGet(() -> OAuthToken.builder()
                .user(user)
                .provider(provider)
                .build()
        );
        toSave.setAccessToken(accessToken);
        toSave.setRefreshToken(refreshToken);
        toSave.setExpiresAt(expiresAt);

        tokenRepository.save(toSave);

        // Redirect wherever you want after login
        response.sendRedirect("/swagger-ui/index.html"); // or /dashboard
    }
}
