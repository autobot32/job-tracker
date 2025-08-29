package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.auth.UserRepository;
import com.atakant.emailtracker.auth.User;
import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.gmail.GmailMessage;
import com.atakant.emailtracker.service.CandidateEmailService;
import com.atakant.emailtracker.service.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final GmailService gmailService;
    private final CandidateEmailService candidateEmailService;
    private  final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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

            int saved = candidateEmailService.processEmails(userId, ingested);

            System.out.println("processed emails");

            model.addAttribute("payload",
                    "Fetched & saved " + ingested.size() + " emails; saved " + saved + " applications.");

            return "redirect:http://localhost:5173/applications";
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
}


