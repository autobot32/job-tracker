package com.atakant.emailtracker.controller;

import com.atakant.emailtracker.service.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final GmailService gmailService;  // <-- this is the 'gmailService' you were missing

    @PostMapping("/preview")
    public String preview(Model model,
                          @org.springframework.security.core.annotation.AuthenticationPrincipal OAuth2User principal,
                          Authentication authentication) {
        model.addAttribute("email", principal.getAttribute("email"));
        try {
            String payload = gmailService.listMessages(authentication, 25);
            model.addAttribute("payload", payload);
        } catch (Exception e) {
            model.addAttribute("payload", "Gmail API error: " + e.getMessage());
        }
        return "dashboard";
    }
}

