package com.atakant.emailtracker.service;

import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.repo.EmailRepository;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import jakarta.mail.internet.MailDateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private final OAuth2AuthorizedClientManager clientManager;
    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final Gmail gmail;

    /**
     * Fetch emails after a given date (YYYY/MM/DD).
     */
    public List<Email> fetchEmailsSince(Authentication authentication, String afterYyyyMmDd) throws Exception {
        final int pageSize = 25;
        final String q = "after:" + afterYyyyMmDd + " in:anywhere -in:chats";

        String pageToken = null;
        List<Email> emails = new ArrayList<>();

        do {
            // List messages with query + pagination

            ListMessagesResponse resp = gmail.users().messages()
                    .list("me")
                    .setLabelIds(List.of("INBOX"))   // <- use label, not category
                    .setQ("after:" + afterYyyyMmDd + " -in:chats")
                    .setIncludeSpamTrash(false)
                    .setMaxResults((long) pageSize)
                    .setFields("messages(id,threadId),nextPageToken,resultSizeEstimate")
                    .setPageToken(pageToken)
                    .execute();
            log.info("After resp");

            List<Message> msgs = resp.getMessages();
            if (msgs == null || msgs.isEmpty()) break;

            OAuth2User principal = (OAuth2User) authentication.getPrincipal();

            log.info("Before findbyemail");
            User user = userRepository.findByEmail(principal.getAttribute("email"))
                    .orElseThrow(() -> new IllegalStateException("User not found"));
            log.info("After findbyemail");

            for (Message m : msgs) {
                // Fetch full message
                log.info("fetching message");
                Message full = gmail.users().messages().get("me", m.getId())
                        .setFormat("full")
                        .setFields("id,threadId,payload")
                        .execute();



                var headers = full.getPayload().getHeaders();
                String dateHeader = header(headers, "Date");
                String subject    = header(headers, "Subject");
                String from       = header(headers, "From");
                String to         = header(headers, "To");

                String body = extractBodyText(full.getPayload());

                Email email = Email.builder()
                        .userId(user.getId())
                        .gmailId(full.getId())
                        .threadId(full.getThreadId())
                        .messageIdHash(header(headers, "Message-ID"))
                        .fromAddr(from)
                        .toAddr(to)
                        .subject(subject)
                        .sentAt(parseDateHeader(dateHeader))
                        .bodyText(body)
                        .internalDateMs(full.getInternalDate() == null ? 0L : full.getInternalDate())
                        .build();

                emails.add(email);
            }

            log.info("added all emails");

            pageToken = resp.getNextPageToken();
        } while (pageToken != null);

        log.info("Fetched {} emails", emails.size());
        emailRepository.saveAll(emails);

        return emails;
    }

    /**
     * Helper to extract a header by name.
     */
    private String header(List<MessagePartHeader> headers, String name) {
        if (headers == null) return "";
        for (MessagePartHeader h : headers) {
            if (name.equalsIgnoreCase(h.getName())) {
                return h.getValue();
            }
        }
        return "";
    }

    /**
     * Recursive helper to extract plain text from message parts.
     */
    private String extractBodyText(MessagePart part) {
        if (part == null) return "";

        String mime = part.getMimeType();

        if ("text/plain".equalsIgnoreCase(mime)) {
            return decode(part.getBody());
        }
        if ("text/html".equalsIgnoreCase(mime)) {
            return stripHtml(decode(part.getBody()));
        }

        if (part.getParts() != null) {
            StringBuilder sb = new StringBuilder();
            for (MessagePart p : part.getParts()) {
                String child = extractBodyText(p);
                if (!child.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(child);
                }
            }
            return sb.toString();
        }

        return "";
    }

    private String decode(MessagePartBody body) {
        if (body == null || body.getData() == null) return "";
        byte[] bytes = Base64.getUrlDecoder().decode(body.getData());
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

    private Instant parseDateHeader(String headerValue) {
        try {
            return new MailDateFormat().parse(headerValue).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Optional: directly resolve a fresh access token if you ever need it.
     */
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
}



