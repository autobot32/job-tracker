package com.atakant.emailtracker.security;

import com.atakant.emailtracker.auth.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    @Transactional // <- keep user + token in one persistence context
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        // 1) Determine registrationId and email
        String registrationId = (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken t)
                ? t.getAuthorizedClientRegistrationId()
                : "google";

        String email;
        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser oidc) {
            email = oidc.getEmail();
        } else if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User oauth2) {
            Object v = oauth2.getAttributes().get("email");
            if (v != null) email = v.toString();
            else {
                email = null;
            }
        } else {
            email = null;
        }

        // 2) Upsert user (managed)
        User user = null;
        if (email != null) {
            user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(
                            User.builder().email(email).provider(registrationId).build()
                    ));
        }

        if (user != null) {
            // Managed reference (no detached entity issues)
            var managedUser = userRepository.getReferenceById(user.getId());

            // 3) Load authorized client (tokens)
            OAuth2AuthorizedClient client =
                    authorizedClientService.loadAuthorizedClient(registrationId, authentication.getName());

            if (client != null && client.getAccessToken() != null) {
                String accessToken  = client.getAccessToken().getTokenValue();
                Instant expiresAt   = client.getAccessToken().getExpiresAt();
                String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;

                // 4) Upsert OAuthToken
                OAuthToken token = tokenRepository.findByUserId(managedUser.getId())
                        .orElseGet(OAuthToken::new);

                // IMPORTANT: Let @MapsId set the PK from the user association.
                token.setUser(managedUser);
                token.setProvider(registrationId);
                token.setAccessToken(accessToken);
                token.setRefreshToken(refreshToken);
                token.setExpiresAt(expiresAt);

                // Do NOT call token.setUserId(...).
                tokenRepository.save(token); // will persist if new, update if existing
            }
        }

        var redirector = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
        redirector.setDefaultTargetUrl("https://www.urbandictionary.com/define.php?term=atakan"); // your choice
        redirector.setAlwaysUseDefaultTargetUrl(false);
        redirector.onAuthenticationSuccess(request, response, authentication);
    }
}
