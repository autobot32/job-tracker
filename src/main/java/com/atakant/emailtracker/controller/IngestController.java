package com.atakant.emailtracker.web;

import com.atakant.emailtracker.service.GmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Controller
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final GmailService gmailService;

    @PostMapping("/preview") // reuse the existing button; now it also saves
    public String fetchAndSave(Model model,
                               @RequestParam(name = "after", required = false) String afterYyyyMmDd,
                               Authentication authentication) {
        String email = ((OAuth2User) authentication.getPrincipal()).getAttribute("email");
        model.addAttribute("email", email);

        String after = (afterYyyyMmDd == null || afterYyyyMmDd.isBlank())
                ? lastNDaysAsGmailDate(30)
                : afterYyyyMmDd;

        try {
            var saved = gmailService.ingestAndSave(authentication, after);
            model.addAttribute("payload", "Saved " + saved.size() + " emails since " + after + ".");
        } catch (Exception e) {
            model.addAttribute("payload", "Error: " + e.getMessage());
        }
        return "dashboard";
    }

    private String lastNDaysAsGmailDate(int n) {
        var cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(n);
        return cutoff.getYear() + "/" +
                String.format("%02d", cutoff.getMonthValue()) + "/" +
                String.format("%02d", cutoff.getDayOfMonth());
    }
}


