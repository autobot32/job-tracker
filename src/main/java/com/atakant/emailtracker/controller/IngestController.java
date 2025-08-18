package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.domain.Email;
import com.atakant.emailtracker.service.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final GmailService gmailService;

    @PostMapping("/preview")
    public String preview(Model model,
                          @AuthenticationPrincipal OAuth2User principal,
                          Authentication authentication) {
        model.addAttribute("email", principal.getAttribute("email"));
        try {
            List<Email> emails = gmailService.fetchEmailsSince(authentication, "2025-08-10");
            model.addAttribute("emails", emails);
        } catch (Exception e) {
            model.addAttribute("error", "Gmail API error: " + e.getMessage());
        }
        return "dashboard";
    }

}

