package com.atakant.emailtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {
    private final OAuth2AuthorizedClientManager clientManager;
    private final RestTemplate rest = new RestTemplate();

    public String listMessages(Authentication authentication, int max) {
        OAuth2AuthorizeRequest req = OAuth2AuthorizeRequest
                .withClientRegistrationId("google")
                .principal(authentication)
                .build();

        OAuth2AuthorizedClient client = clientManager.authorize(req);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No authorized Google client for current user.");
        }

        var tok = client.getAccessToken();
        log.info("Access token expiresAt={} scopes={}", tok.getExpiresAt(), tok.getScopes());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tok.getTokenValue());
        String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=" + max;

        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }
}

