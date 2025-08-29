package com.atakant.emailtracker.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import com.google.api.client.http.HttpRequest;



@Configuration
public class GmailConfig {

    @Bean
    public Gmail gmail(OAuth2AuthorizedClientManager clientManager) throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = JacksonFactory.getDefaultInstance();

        HttpRequestInitializer requestInitializer = (HttpRequest request) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            OAuth2AuthorizeRequest ar = OAuth2AuthorizeRequest.withClientRegistrationId("google")
                    .principal(auth)
                    .build();
            OAuth2AuthorizedClient client = clientManager.authorize(ar);
            if (client == null || client.getAccessToken() == null) {
                throw new IllegalStateException("No authorized Google client/token for current user");
            }
            OAuth2AccessToken token = client.getAccessToken();
            request.getHeaders().setAuthorization("Bearer " + token.getTokenValue());
        };

        return new Gmail.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("Email Job Tracker")
                .build();
    }
}
