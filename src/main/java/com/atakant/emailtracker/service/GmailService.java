package com.atakant.emailtracker.service;

import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import com.atakant.emailtracker.domain.Email;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import jakarta.mail.internet.MailDateFormat;
import java.util.UUID;
import org.springframework.security.oauth2.core.user.OAuth2User;



import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {
    private final OAuth2AuthorizedClientManager clientManager;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UserRepository userRepository;

    public List<Email> fetchEmailsSince(Authentication authentication, String afterYyyyMmDd) throws Exception {
        String token = resolveAccessToken(authentication);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        final int pageSize = 25;
        final String q = "after:" + afterYyyyMmDd + " category:primary -in:chats";

        String nextPageToken = null;
        List<Email> emails = new ArrayList<>();

        do {
            // Step 1: List messages
            String url = "https://gmail.googleapis.com/gmail/v1/users/me/messages"
                    + "?maxResults=" + pageSize
                    + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                    + (nextPageToken != null ? "&pageToken=" + nextPageToken : "");

            String listJson = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            JsonNode list = mapper.readTree(listJson);
            JsonNode ids = list.path("messages");
            nextPageToken = list.has("nextPageToken") ? list.get("nextPageToken").asText(null) : null;

            if (!ids.isArray() || ids.isEmpty()) break;

            for (JsonNode node : ids) {
                String id = node.path("id").asText();

                // Step 2: Get full message
                String getUrl = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" + id + "?format=full";
                String fullJson = rest.exchange(getUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
                JsonNode msg = mapper.readTree(fullJson);

                // Extract headers
                JsonNode headersNode = msg.path("payload").path("headers");
                String dateHeader = header(headersNode, "Date");
                String subject = header(headersNode, "Subject");
                String from = header(headersNode, "From");
                String to = header(headersNode, "To");

                // Extract body
                String body = extractBodyText(msg.path("payload"));

                OAuth2User principal = (OAuth2User) authentication.getPrincipal();
                User user = userRepository.findByEmail(principal.getAttribute("email"))
                        .orElseThrow(() -> new IllegalStateException("User not found"));
                UUID userId = user.getId();


                // Build Email object
                Email email = Email.builder()
                        .userId(userId)
                        .gmailId(msg.path("id").asText(""))
                        .threadId(msg.path("threadId").asText(""))
                        .messageIdHash(header(headersNode, "Message-ID")) // safe unique identifier
                        .fromAddr(from)
                        .toAddr(to)
                        .subject(subject)
                        .sentAt(parseDateHeader(dateHeader)) // helper to parse into Instant
                        .bodyText(body)
                        .internalDateMs(msg.path("internalDate").asLong(0))
                        .build();

                emails.add(email);
            }
        } while (nextPageToken != null);

        log.info("Fetched {} emails: {}", emails.size(), emails);

        return emails;
    }


    private String header(JsonNode headers, String name) {
        if (headers != null && headers.isArray()) {
            for (JsonNode h : headers) {
                if (name.equalsIgnoreCase(h.path("name").asText())) {
                    return h.path("value").asText("");
                }
            }
        }
        return "";
    }

    private String extractBodyText(JsonNode part) {
        String mime = part.path("mimeType").asText();
        if ("text/plain".equalsIgnoreCase(mime)) {
            return decodeBody(part.path("body"));
        }
        if ("text/html".equalsIgnoreCase(mime)) {
            return stripHtml(decodeBody(part.path("body")));
        }
        JsonNode parts = part.path("parts");
        if (parts != null && parts.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : parts) {
                String child = extractBodyText(p);
                if (!child.isEmpty()) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(child);
                }
            }
            return sb.toString();
        }
        return "";
    }

    private String decodeBody(JsonNode body) {
        String data = body.path("data").asText("");
        if (data.isEmpty()) return "";
        byte[] bytes = Base64.getUrlDecoder().decode(data);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String stripHtml(String html) {
        return html.replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private String resolveAccessToken(Authentication authentication) {
        OAuth2AuthorizeRequest req = OAuth2AuthorizeRequest
                .withClientRegistrationId("google")
                .principal(authentication)
                .build();

        OAuth2AuthorizedClient client = clientManager.authorize(req);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No authorized Google client for current user.");
        }

        return client.getAccessToken().getTokenValue();
    }

    private Instant parseDateHeader(String headerValue) {
        try {
            return new MailDateFormat().parse(headerValue).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

}


