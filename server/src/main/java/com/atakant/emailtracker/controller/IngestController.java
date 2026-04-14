package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.auth.UserRepository;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.service.CandidateEmailService;
import com.atakant.emailtracker.service.GmailService;
import com.atakant.emailtracker.service.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = { "http://localhost:5173", "http://localhost:3000" }, allowCredentials = "true")
@Controller
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final GmailService gmailService;
    private final CandidateEmailService candidateEmailService;
    private  final UserRepository userRepository;

    @PostMapping("/preview")
    public String preview(Model model,
                          @org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User principal,
                          Authentication authentication,
                          @RequestParam(name = "after", required = false) String afterStr) {
        model.addAttribute("email", principal.getAttribute("email"));
        try {
            String afterArg = (afterStr == null || afterStr.isBlank()) ? null : afterStr.trim();
            List<Email> ingested = gmailService.ingestAndSave(authentication, afterArg);

            UUID userId = resolveCurrentUserId(principal);

            CandidateEmailService.ProcessEmailsResult result = candidateEmailService.processEmails(userId, ingested);

            System.out.println("processed emails");

            model.addAttribute("payload",
                    "Fetched & saved " + ingested.size() + " emails; found "
                            + result.candidateEmailsFound() + " candidate emails; processed "
                            + result.candidateEmailsProcessed() + "; saved "
                            + result.saved() + " applications."
                            + (result.quotaTruncated() ? " " + result.quotaMessage() : ""));

            return "redirect:http://localhost:5173/applications";
        } catch (RateLimitExceededException e) {
            model.addAttribute("payload", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("payload", "Error: " + e.getMessage());
        }
        return "dashboard";
    }

    private UUID resolveCurrentUserId(OAuth2User principal) {
        String email = principal.getAttribute("email");
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("No user found for email: " + email));
    }

    @PostMapping("/run-json")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> runJson(
            Model model,
            @org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User principal,
            Authentication authentication,
            @RequestParam(name = "after", required = false) String afterStr
    ) {
        try {
            String afterArg = (afterStr == null || afterStr.isBlank()) ? null : afterStr.trim();

            java.util.List<Email> ingested = gmailService.ingestAndSave(authentication, afterArg);

            java.util.UUID userId = resolveCurrentUserId(principal);
            CandidateEmailService.ProcessEmailsResult result = candidateEmailService.processEmails(userId, ingested);

            return ResponseEntity.ok(java.util.Map.of(
                    "ok", true,
                    "emails", ingested.size(),
                    "candidateEmailsFound", result.candidateEmailsFound(),
                    "candidateEmailsProcessed", result.candidateEmailsProcessed(),
                    "saved", result.saved(),
                    "quotaTruncated", result.quotaTruncated(),
                    "quotaMessage", result.quotaMessage(),
                    "remainingRunsToday", result.remainingRunsToday(),
                    "remainingLlmEmailsToday", result.remainingLlmEmailsToday()
            ));
        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(java.util.Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        }
    }
}

