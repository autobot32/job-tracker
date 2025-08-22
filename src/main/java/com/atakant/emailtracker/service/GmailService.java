package com.atakant.emailtracker.service;

import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.gmail.GmailMessage;
import com.atakant.emailtracker.repo.EmailRepository;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import jakarta.mail.internet.MailDateFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private final OAuth2AuthorizedClientManager clientManager;
    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final Gmail gmail;

    /**
     * Fetch Gmail messages since a given date (format YYYY/MM/DD).
     */
    public List<GmailMessage> fetchMessagesSince(Authentication authentication, String afterYyyyMmDd) throws Exception {
        final int pageSize = 25;
        final String query = "after:" + afterYyyyMmDd + " -in:chats";

        String pageToken = null;
        List<GmailMessage> results = new ArrayList<>();
        Map<String, String> labelNameById = loadLabelNameMap();

        do {
            ListMessagesResponse resp = gmail.users().messages()
                    .list("me")
                    .setLabelIds(List.of("INBOX"))
                    .setQ(query)
                    .setIncludeSpamTrash(false)
                    .setMaxResults((long) pageSize)
                    .setFields("messages(id,threadId),nextPageToken")
                    .setPageToken(pageToken)
                    .execute();

            List<Message> summary = resp.getMessages();
            if (summary == null || summary.isEmpty()) break;

            for (Message m : summary) {
                Message full = gmail.users().messages().get("me", m.getId())
                        .setFormat("full")
                        .setFields("id,threadId,internalDate,labelIds,payload")
                        .execute();

                MessagePart payload = full.getPayload();
                List<MessagePartHeader> headers = payload != null ? payload.getHeaders() : Collections.emptyList();

                String rfc822 = header(headers, "Message-ID");
                String dateHdr = header(headers, "Date");
                String subject = header(headers, "Subject");
                String from    = header(headers, "From");
                String to      = header(headers, "To");

                long internalMs = full.getInternalDate() != null ? full.getInternalDate() : 0L;
                OffsetDateTime sentAtUtc = toUtc(dateHdr, internalMs);

                String bodyText = extractBodyText(payload);
                List<String> labels = toLabelNames(full.getLabelIds(), labelNameById);

                GmailMessage dto = new GmailMessage(
                        full.getId(),
                        full.getThreadId(),
                        isBlank(rfc822) ? null : rfc822,
                        internalMs,
                        nullToEmpty(from),
                        nullToEmpty(to),
                        nullToEmpty(subject),
                        sentAtUtc,
                        bodyText,
                        labels
                );

                results.add(dto);
            }

            pageToken = resp.getNextPageToken();
        } while (pageToken != null);

        log.info("Fetched {} GmailMessage DTOs", results.size());
        return results;
    }

    /**
     * Map DTOs to Email entities and persist them.
     */
    public List<Email> ingestAndSave(Authentication authentication, String afterYyyyMmDd) throws Exception {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        User user = userRepository.findByEmail(principal.getAttribute("email"))
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<GmailMessage> dtos = fetchMessagesSince(authentication, afterYyyyMmDd);
        List<Email> emails = new ArrayList<>(dtos.size());

        for (GmailMessage g : dtos) {
            String msgIdHash = (g.rfc822MessageId() != null && !g.rfc822MessageId().isBlank())
                    ? g.rfc822MessageId()
                    : g.gmailId();

            Email e = Email.builder()
                    .userId(user.getId())
                    .gmailId(g.gmailId())
                    .threadId(g.threadId())
                    .messageIdHash(msgIdHash)
                    .fromAddr(g.from())
                    .toAddr(g.to())
                    .subject(g.subject())
                    .sentAt(g.sentAtUtc() != null ? g.sentAtUtc().toInstant() : null)
                    .bodyText(g.bodyText())
                    .internalDateMs(g.internalDateMs())
                    .rawLabel(String.join(",", g.labels()))
                    .build();

            emails.add(e);
        }

        emailRepository.saveAll(emails);
        return emails;
    }

    private Map<String, String> loadLabelNameMap() throws Exception {
        Map<String, String> map = new HashMap<>();
        ListLabelsResponse labelsResponse = gmail.users().labels().list("me").execute();
        if (labelsResponse.getLabels() != null) {
            for (Label l : labelsResponse.getLabels()) {
                map.put(l.getId(), l.getName());
            }
        }
        return map;
    }

    private List<String> toLabelNames(List<String> ids, Map<String, String> nameById) {
        if (ids == null) return List.of();
        List<String> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            out.add(nameById.getOrDefault(id, id));
        }
        return out;
    }

    private String header(List<MessagePartHeader> headers, String name) {
        if (headers == null) return null;
        for (MessagePartHeader h : headers) {
            if (name.equalsIgnoreCase(h.getName())) return h.getValue();
        }
        return null;
    }

    private OffsetDateTime toUtc(String dateHeader, long internalDateMs) {
        try {
            if (!isBlank(dateHeader)) {
                Instant parsed = new MailDateFormat().parse(dateHeader).toInstant();
                return parsed.atOffset(ZoneOffset.UTC);
            }
        } catch (Exception ignore) {}
        if (internalDateMs > 0) {
            return Instant.ofEpochMilli(internalDateMs).atOffset(ZoneOffset.UTC);
        }
        return null;
    }

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
                    if (sb.length() > 0) sb.append("\n");
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
        if (html == null) return "";
        return html
                .replaceAll("(?is)<style.*?</style>", "")
                .replaceAll("(?is)<script.*?</script>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
    private String nullToEmpty(String s) { return s == null ? "" : s; }

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



