package com.atakant.emailtracker.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2SuccessHandler successHandler;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
        // Log what’s in application.yml
        ClientRegistration reg = clients.findByRegistrationId("google");
        log.info("Boot registration 'google' scopes at startup = {}", reg.getScopes());

        var baseUri  = OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI;
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(clients, baseUri);

        // Force scopes + offline + consent so we actually get a token with Gmail scope
        delegate.setAuthorizationRequestCustomizer(c -> {
            c.scopes(Set.of(
                    "openid", "profile", "email",
                    "https://www.googleapis.com/auth/gmail.readonly" // or gmail.modify
            ));
            c.additionalParameters(p -> {
                p.put("access_type", "offline");
                p.put("prompt", "consent");
                p.put("include_granted_scopes", "true");
            });
        });

        // Wrapper that logs the scopes we send to Google
        OAuth2AuthorizationRequestResolver loggingResolver = new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest r = delegate.resolve(request);
                if (r != null) log.info("Authorization request scopes sent to Google = {}", r.getScopes());
                return r;
            }
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                OAuth2AuthorizationRequest r = delegate.resolve(request, clientRegistrationId);
                if (r != null) log.info("Authorization request scopes sent to Google = {}", r.getScopes());
                return r;
            }
        };

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/health").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                                .authorizationEndpoint(ep -> ep.authorizationRequestResolver(loggingResolver))
                                .successHandler(successHandler)
                )
                .logout(l -> l.logoutSuccessUrl("/"));

        // Do NOT declare your own OAuth2AuthorizedClientManager bean anywhere.
        return http.build();
    }
}






