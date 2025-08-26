package com.atakant.emailtracker.service;

import com.atakant.emailtracker.repo.EmailRepository;
import com.atakant.emailtracker.repo.ApplicationRepository;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.domain.Application;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CandidateEmailService {

    private final EmailRepository emailRepo;
    private final ApplicationRepository appRepo;
    private final LlmClient llm;

    private static final String[] KEYWORDS = {
            "application", "applied", "assessment", "coding challenge",
            "interview", "status update", "thank you for applying",
            "we received your application", "oa", "take-home", "hackerrank"
    };

    @Transactional
    public int processEmails(UUID userId, List<Email> emails) {
        int processed = 0, saved = 0, skippedNonJob = 0, skippedMissing = 0, failed = 0;
        System.out.println("Processing " + emails.size() + " emails");
        for (Email e : emails) {
            System.out.println("entered");
            if (!looksLikeCandidate(e)) continue;
            var parsed = llm.extractApplication(buildPrompt(e));
            if (parsed == null) {
                continue;
            }
            if (isBlank(parsed.getCompany()) || isBlank(parsed.getRoleTitle())) {

                skippedMissing++;
                continue;
            }
            try {
                upsertOne(userId, e.getId(), parsed);
                saved++;
                System.out.println("processed an email");
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                failed++;
                System.err.println("Failed to save application for email " + e.getId() + ": " + ex.getMessage());
            }
            processed++;
        }
        System.out.printf("apps: saved=%d, skippedNonJob=%d, skippedMissing=%d, failed=%d%n",
                saved, skippedNonJob, skippedMissing, failed);
        return processed;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertOne(UUID userId, UUID emailId, LlmClient.ApplicationExtractionResult parsed) {
        upsertApplication(userId, emailId, parsed);
    }


    private boolean looksLikeCandidate(Email e) {
        String hay = ((e.getSubject() == null ? "" : e.getSubject()) + " " +
                (e.getBodyText() == null ? "" : e.getBodyText()))
                .toLowerCase();
        for (String kw : KEYWORDS) {
            if (hay.contains(kw)) return true;
        }
        // quick ATS domain check
        String from = e.getFromAddr() == null ? "" : e.getFromAddr().toLowerCase();
        if (from.contains("greenhouse") || from.contains("lever") ||
                from.contains("workday") || from.contains("smartrecruiters") ||
                from.contains("icims") || from.contains("brassring"))
            return true;

        return false;
    }

    private String buildPrompt(Email e) {
        String subject = e.getSubject() == null ? "" : e.getSubject();
        String from    = e.getFromAddr() == null ? "" : e.getFromAddr();
        String body    = e.getBodyText() == null ? "" : e.getBodyText();

        return """
        Extract job-application info from the email below and return ONLY a single **minified** JSON object.
        Keys (all REQUIRED, never null/empty):
        {
          "company": string,                // use "(unknown)" if unsure
          "role_title": string,             // use "(unknown)" if unsure
          "location": string,               // use "(unknown)" if missing
          "status": "applied"|"assessment"|"interview"|"offer"|"rejected"|"other", // pick "other" if unclear
          "next_action": string,            // use "(unknown)" if missing
          "notes": string                   // brief summary or "(unknown)"
        }
        Rules:
        - No markdown, no code fences, no extra text before/after the JSON.
        - Output must be valid JSON on one line (minified).
        
        SUBJECT: %s
        FROM: %s
        BODY:
        %s
        """.formatted(subject, from, body);
    }


    private static String safe(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private boolean merge(Supplier<String> getter, Consumer<String> setter, String newValue) {
        if (newValue == null || newValue.isBlank()) return false;
        if (!Objects.equals(getter.get(), newValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }


    private void upsertApplication(UUID userId, UUID emailId, LlmClient.ApplicationExtractionResult x) {
        Application existing = appRepo
                .findFirstByUserIdAndCompanyIgnoreCaseAndRoleTitleIgnoreCase(
                        userId, safe(x.getCompany()), safe(x.getRoleTitle()))
                .orElse(null);

        if (existing == null) {
            Application a = new Application();
            a.setId(UUID.randomUUID());
            a.setUserId(userId);
            a.setCompany(safe(x.getCompany()));
            a.setRoleTitle(safe(x.getRoleTitle()));
            a.setLocation(safe(x.getLocation()));
            a.setStatus(safe(x.getStatus()));
            a.setNextStep(safe(x.getNextAction()));
            a.setNotes(safe(x.getNotes()));
            a.setFirstSeenAt(OffsetDateTime.now());
            a.setSourceEmailId(emailId);
            a.setLastUpdatedAt(OffsetDateTime.now());
            appRepo.save(a);
        } else {
            boolean dirty = false;
            dirty |= merge(existing::getLocation, existing::setLocation, x.getLocation());
            dirty |= merge(existing::getStatus, existing::setStatus, x.getStatus());
            dirty |= merge(existing::getNextStep, existing::setNextStep, x.getNextAction());
            dirty |= merge(existing::getNotes, existing::setNotes, x.getNotes());
            if (existing.getSourceEmailId() == null) {
                existing.setSourceEmailId(emailId);
                dirty = true;
            }
            if (dirty) {
                existing.setLastUpdatedAt(OffsetDateTime.now());
                appRepo.save(existing);
            }
        }
    }

}
