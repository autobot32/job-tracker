package com.atakant.emailtracker.security;

import com.atakant.emailtracker.auth.OAuthToken;
import com.atakant.emailtracker.auth.OAuthTokenRepository;
import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.info(">>> ENTER CustomOAuth2SuccessHandler");

        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;

        // 1) Get email from principal
        var principal = oauth.getPrincipal();
        String email = principal.getAttribute("email");
        if (email == null) {
            email = principal.getAttribute("emailAddress");
        }
        if (email == null) {
            log.error("OAuth login succeeded but no email attribute found on principal: {}", principal.getAttributes());
            response.sendRedirect("/?authError=noEmail");
            return;
        }

        // 2) Upsert user (make sure it's saved/managed)
        String finalEmail = email;
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(finalEmail);
            u.setProvider(oauth.getAuthorizedClientRegistrationId());
            return userRepository.save(u);
        });
        // ensure managed state
        user = userRepository.save(user);

        log.info(">>> User upserted id={}, email={}", user.getId(), user.getEmail());

        // 3) Load OAuth client (access/refresh tokens)
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauth.getAuthorizedClientRegistrationId(),
                oauth.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            log.error("Authorized client or access token is null for user {}", email);
            response.sendRedirect("/?authError=noAccessToken");
            return;
        }

        String accessToken = client.getAccessToken().getTokenValue();
        Instant expiresAt = client.getAccessToken().getExpiresAt();
        String refreshToken = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;

        log.info("Saving access token prefix={}", accessToken.substring(0, 8));

        // 4) Upsert OAuthToken (with @MapsId, don't touch userId manually)
        User finalUser = user;
        OAuthToken token = tokenRepository.findById(user.getId()).orElseGet(() -> {
            OAuthToken t = new OAuthToken();
            t.setUser(finalUser); // @MapsId will propagate the PK
            return t;
        });

        token.setUser(user); // always keep attached
        token.setProvider("google");
        token.setAccessToken(accessToken);
        token.setRefreshToken(refreshToken);
        token.setExpiresAt(expiresAt);

        tokenRepository.save(token);

        // 5) Redirect
        SavedRequestAwareAuthenticationSuccessHandler redirector = new SavedRequestAwareAuthenticationSuccessHandler();
        redirector.setDefaultTargetUrl("http://localhost:5173/applications");
        redirector.setAlwaysUseDefaultTargetUrl(true);
        redirector.onAuthenticationSuccess(request, response, authentication);
    }
}
