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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;


@Slf4j
@Service
@RequiredArgsConstructor
public class GmailService {

    private final OAuth2AuthorizedClientManager clientManager;
    private final UserRepository userRepository;
    private final EmailRepository emailRepository;
    private final Gmail gmail;
    private final ThreadPoolTaskExecutor fetchExecutor;

    // Fetch gmail messages since a given date
    public List<GmailMessage> fetchMessagesSince(Authentication authentication, String afterYyyyMmDd) throws Exception {
        final int pageSize = 50;
        final String query = "after:" + afterYyyyMmDd + " -in:chats";

        String accessToken = resolveAccessToken(authentication);

        var transport   = gmail.getRequestFactory().getTransport();
        var jsonFactory = gmail.getJsonFactory();
        String appName  = gmail.getApplicationName();

        com.google.api.client.http.HttpRequestInitializer init = req ->
                req.getHeaders().setAuthorization("Bearer " + accessToken);

        Gmail tokenGmail = new Gmail.Builder(transport, jsonFactory, init)
                .setApplicationName(appName)
                .build();

        String pageToken = null;
        List<GmailMessage> results = new ArrayList<>();

        Map<String, String> labelNameById = loadLabelNameMap(tokenGmail);

        do {
            ListMessagesResponse resp = tokenGmail.users().messages()
                    .list("me")
                    .setLabelIds(List.of("INBOX"))
                    .setQ(query)
                    .setIncludeSpamTrash(false)
                    .setMaxResults((long) pageSize)
                    .setFields("messages/id,nextPageToken")
                    .setPageToken(pageToken)
                    .execute();

            List<Message> summary = resp.getMessages();
            if (summary == null || summary.isEmpty()) break;

            var rawPool = fetchExecutor.getThreadPoolExecutor();

            List<CompletableFuture<GmailMessage>> futures = new ArrayList<>(summary.size());
            for (Message m : summary) {
                try {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> fetchOneMessageAsDto(tokenGmail, m.getId(), labelNameById),
                            rawPool
                    ));
                } catch (RejectedExecutionException rex) {
                    GmailMessage dto = fetchOneMessageAsDto(tokenGmail, m.getId(), labelNameById);
                    futures.add(CompletableFuture.completedFuture(dto));
                }
            }


            for (var f : futures) {
                GmailMessage dto = f.join();
                if (dto != null) results.add(dto);
            }

            pageToken = resp.getNextPageToken();
        } while (pageToken != null);

        log.info("Fetched {} GmailMessage DTOs concurrently", results.size());
        return results;
    }

    private Map<String, String> loadLabelNameMap(Gmail client) throws Exception {
        Map<String, String> map = new HashMap<>();
        ListLabelsResponse labelsResponse = client.users().labels().list("me").execute();
        if (labelsResponse.getLabels() != null) {
            for (Label l : labelsResponse.getLabels()) {
                map.put(l.getId(), l.getName());
            }
        }
        return map;
    }

    // Maps gmail message to unique user
    public List<Email> ingestAndSave(Authentication authentication, String afterYyyyMmDd) throws Exception {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        User user = userRepository.findByEmail(principal.getAttribute("email"))
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<GmailMessage> dtos = fetchMessagesSince(authentication, afterYyyyMmDd);
        List<Email> saved = new ArrayList<>();

        for (GmailMessage g : dtos) {
            String msgIdHash = (g.rfc822MessageId() != null && !g.rfc822MessageId().isBlank())
                    ? g.rfc822MessageId()
                    : g.gmailId();

            // check if this (user, messageIdHash) already exists
            if (emailRepository.existsByUserIdAndMessageIdHash(user.getId(), msgIdHash)) {
                continue;
            }

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

            saved.add(emailRepository.save(e));
        }
        return saved;
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


    private GmailMessage fetchOneMessageAsDto(Gmail client, String gmailId, Map<String, String> labelNameById) {
        try {
            Message full = client.users().messages().get("me", gmailId)
                    .setFormat("full")
                    .setFields("id,threadId,internalDate,labelIds,payload")
                    .execute();

            MessagePart payload = full.getPayload();
            List<MessagePartHeader> headers = (payload != null) ? payload.getHeaders() : java.util.Collections.emptyList();

            String rfc822  = header(headers, "Message-ID");
            String dateHdr = header(headers, "Date");
            String subject = header(headers, "Subject");
            String from    = header(headers, "From");
            String to      = header(headers, "To");

            long internalMs = (full.getInternalDate() != null) ? full.getInternalDate() : 0L;
            java.time.OffsetDateTime sentAtUtc = toUtc(dateHdr, internalMs);

            String bodyText = extractBodyText(payload);
            List<String> labels = toLabelNames(full.getLabelIds(), labelNameById);

            return new GmailMessage(
                    full.getId(),
                    full.getThreadId(),
                    (rfc822 != null && !rfc822.isBlank()) ? rfc822 : null,
                    internalMs,
                    nullToEmpty(from),
                    nullToEmpty(to),
                    nullToEmpty(subject),
                    sentAtUtc,
                    bodyText,
                    labels
            );
        } catch (Exception e) {
            log.warn("Gmail GET failed for id {}: {}", gmailId, e.toString());
            return null;
        }
    }

    @Transactional
    public void deleteAllForUser(UUID userId) {
        emailRepository.deleteByUserId(userId);
    }



}



