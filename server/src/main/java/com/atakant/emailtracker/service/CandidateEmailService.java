package com.atakant.emailtracker.service;

import com.atakant.emailtracker.repo.EmailRepository;
import com.atakant.emailtracker.repo.ApplicationRepository;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.domain.Application;

import com.atakant.emailtracker.utils.AppNorm;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.CompletableFuture;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CandidateEmailService {

    private final EmailRepository emailRepo;
    private final ApplicationRepository appRepo;
    private final LlmClient llm;
    private final ThreadPoolTaskExecutor parseExecutor;

    private static final String[] KEYWORDS = {
            "application", "applied", "assessment", "coding challenge",
            "interview", "status update", "thank you for applying",
            "we received your application", "oa", "take-home", "hackerrank"
    };

    @Transactional
    public int processEmails(UUID userId, List<Email> emails) {
        List<Email> candidates = emails.stream()
                .filter(this::looksLikeCandidate)
                .toList();

        var rawPool = parseExecutor.getThreadPoolExecutor();

        List<CompletableFuture<Extracted>> futures = new java.util.ArrayList<>(candidates.size());
        for (Email e : candidates) {
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        LlmClient.ApplicationExtractionResult parsed = llm.extractApplication(buildPrompt(e));
                        return new Extracted(e, parsed);
                    } catch (Exception ex) {
                        return new Extracted(e, null);
                    }
                }, rawPool));
            } catch (RejectedExecutionException rex) {
                LlmClient.ApplicationExtractionResult parsed = null;
                try {
                    parsed = llm.extractApplication(buildPrompt(e));
                } catch (Exception ignore) {}
                futures.add(CompletableFuture.completedFuture(new Extracted(e, parsed)));
            }
        }

        int saved = 0, skippedNonJob = 0, failed = 0;
        for (CompletableFuture<Extracted> f : futures) {
            Extracted it;
            try {
                it = f.join();
            } catch (Exception ignore) {
                continue;
            }
            if (it.parsed == null) continue;
            if (!it.parsed.isApplication()) { skippedNonJob++; continue; }
            try {
                upsertApplication(userId, it.email.getId(), it.parsed);
                saved++;
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                failed++;
                System.err.println("Failed to save application for email " + it.email.getId() + ": " + ex.getMessage());
            }
        }
        System.out.printf("apps: saved=%d, skippedNonJob=%d, failed=%d%n", saved, skippedNonJob, failed);
        return saved;
    }

    private static final class Extracted {
        final Email email;
        final LlmClient.ApplicationExtractionResult parsed;
        Extracted(Email e, LlmClient.ApplicationExtractionResult p) { this.email = e; this.parsed = p; }
    }

    private void upsertApplication(UUID userId, UUID emailId, LlmClient.ApplicationExtractionResult x) {
        String normCo = (x.getNormalizedCompany() != null && !x.getNormalizedCompany().isBlank())
                ? AppNorm.normCompany(x.getNormalizedCompany().trim()) // always re-normalize
                : AppNorm.normCompany(x.getCompany());

        String normRole = (x.getNormalizedRoleTitle() != null && !x.getNormalizedRoleTitle().isBlank())
                ? AppNorm.normRole(x.getNormalizedRoleTitle().trim()) // always re-normalize
                : AppNorm.normRole(x.getRoleTitle());

        Application existing = appRepo
                .findByUserIdAndNormalizedCompanyAndNormalizedRoleTitle(userId, normCo, normRole)
                .orElse(null);

        if (existing == null) {
            Application a = new Application();
            a.setId(UUID.randomUUID());
            a.setUserId(userId);
            a.setCompany(emptyToUnknown(x.getCompany()));
            a.setRoleTitle(emptyToUnknown(x.getRoleTitle()));
            a.setNormalizedCompany(normCo);
            a.setNormalizedRoleTitle(normRole);
            a.setLocation(emptyToUnknown(x.getLocation()));
            a.setStatus(emptyToUnknown(x.getStatus()));
            a.setNextStep(emptyToUnknown(x.getNotes()));
            a.setNotes(emptyToUnknown(x.getNotes()));
            a.setApplication(true);
            a.setSourceEmailId(emailId);
            a.setFirstSeenAt(OffsetDateTime.now());
            a.setLastUpdatedAt(OffsetDateTime.now());
            appRepo.save(a);
            return;
        }

        boolean dirty = false;

        // Promote status (never downgrade). “other” won’t override applied/interview/etc.
        String finalStatus = AppNorm.promoteStatus(existing.getStatus(), x.getStatus());
        dirty |= merge(existing::getStatus, existing::setStatus, finalStatus);

        dirty |= merge(existing::getCompany, existing::setCompany, emptyToUnknown(x.getCompany()));
        dirty |= merge(existing::getRoleTitle, existing::setRoleTitle, emptyToUnknown(x.getRoleTitle()));

        // Keep normalized keys stable if LLM gives them
        dirty |= merge(existing::getNormalizedCompany, existing::setNormalizedCompany, normCo);
        dirty |= merge(existing::getNormalizedRoleTitle, existing::setNormalizedRoleTitle, normRole);

        dirty |= merge(existing::getLocation, existing::setLocation,
                AppNorm.mergeLocation(existing.getLocation(), emptyToUnknown(x.getLocation())));
        dirty |= merge(existing::getNextStep, existing::setNextStep, emptyToUnknown(x.getNextAction()));
        dirty |= merge(existing::getNotes, existing::setNotes,
                AppNorm.mergeNotes(existing.getNotes(), emptyToUnknown(x.getNotes())));

        if (existing.getSourceEmailId() == null) { existing.setSourceEmailId(emailId); dirty = true; }

        if (dirty) {
            existing.setLastUpdatedAt(OffsetDateTime.now());
            appRepo.save(existing);
        }
    }


    private static String emptyToUnknown(String s) {
        return (s == null || s.isBlank()) ? "(unknown)" : s.trim();
    }

    private String buildPrompt(Email e) {
        String subject = e.getSubject() == null ? "" : e.getSubject();
        String from    = e.getFromAddr() == null ? "" : e.getFromAddr();
        String body    = e.getBodyText() == null ? "" : e.getBodyText();

        return """
        Extract job-application info from the email below and return ONLY a SINGLE compact, MINIFIED JSON object.
        The JSON MUST contain exactly these keys (all REQUIRED, never null/empty):
    
        {
          "is_application": boolean,           // true if this email is about the user's application lifecycle, false otherwise
          "company": string,                   // "(unknown)" if unsure
          "role_title": string,                // "(unknown)" if unsure
          "location": string,                  // "(unknown)" if missing
          "status": "applied"|"assessment"|"interview"|"offer"|"rejected"|"other", // use "other" if unclear
          "next_action": string,               // "(unknown)" if missing
          "notes": string                      // brief summary or reason; never empty
          "normalized_company": string,        // normalized: lowercase, trimmed, no punctuation
          "normalized_role_title": string      // normalized: lowercase, trimmed, no punctuation, and words sorted alphabetically
        }
    
        CLASSIFICATION RULES:
        - If the email is directly about the user's OWN application lifecycle (thank-you, applied, status update, assessment/OA, interview, offer, rejection, portal updates), then "is_application" must be true — even if some fields are unknown.
        - If it is a newsletter, referral campaign, mentorship, event/community email, marketing, or anything not about the user's candidacy, set "is_application" to false.
    
        EXTRACTION RULES:
        - Never output null or empty strings — use "(unknown)" when needed.
        - Prefer role/company from the SUBJECT or explicit "Thank you for applying to..." lines.
        - Do not infer role from sender signatures (e.g., "Senior SDE" in a signature is not the user's role).
        - Only extract actual job location (like "United States (Remote)", "Seattle, WA"). Ignore addresses in footers.
        - Status must always be one of the six values.
        - Next action should be the most concrete required step for the user ("schedule interview", "complete OA", or "(unknown)" if none).
        - Notes should be short, 1–2 phrases: e.g., "application received", "OA invitation", "interview scheduled", "rejection", "mentorship pairing".
        - IMPORTANT: Do NOT mark "assessment" unless the candidate is asked to take a test or complete an OA with a link/instructions/deadline.
          Phrases about internal recruiter assessment still mean "applied".
        - For normalization: "normalized_company" and "normalized_role_title" must be lowercase, trimmed, and punctuation removed. For "normalized_role_title", also sort the words alphabetically so that e.g. "Software Engineer Intern" and "Intern Software Engineer" are treated as the same.
    
        FORMAT:
        - Output valid minified JSON only.
        - No markdown, no code fences, no explanations.
    
        SUBJECT: %s
        FROM: %s
        BODY:
        %s
        """.formatted(subject, from, body);
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



    private boolean merge(Supplier<String> getter, Consumer<String> setter, String newValue) {
        if (newValue == null || newValue.isBlank()) return false;
        if (!Objects.equals(getter.get(), newValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }
}
