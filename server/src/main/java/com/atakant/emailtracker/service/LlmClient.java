package com.atakant.emailtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmClient {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-5-nano}")
    private String model;

    private static final String SYSTEM = """
You are an information-extraction system for job application emails.

Return ONLY a SINGLE compact, MINIFIED JSON object with EXACTLY these keys (no extra keys, no markdown, no code fences, no explanations):
{
  "is_application": boolean,
  "company": string,
  "role_title": string,
  "location": string,
  "status": "applied" | "assessment" | "interview" | "offer" | "rejected" | "other",
  "next_action": string,
  "notes": string,
  "normalized_company": string,
  "normalized_role_title": string
}

CLASSIFY FIRST:
- "is_application" = true ONLY if this email is directly about the user's OWN candidacy (application received/thank you, portal status, assessment/OA invite the candidate must take, interview scheduling/details, offer, rejection).
- If it's a newsletter, event, referral campaign, mentorship, hiring digest, marketing, or anything not about the user's candidacy, set "is_application": false.

STATUS RULES (be strict and literal):
- "applied": confirmations like "we received your application", “thank you for applying”, “under review”, “a recruiter will assess your application”, or similar. Do NOT mark "assessment" here.
- "assessment": ONLY if the candidate is asked to take a test or complete a task (e.g., explicit OA instructions, a test platform name/link like HackerRank/CodeSignal/Codility/SHL, deadlines for completing a test).
  - Phrases like “a recruiter will complete their assessment” or “our team is assessing your application” DO NOT count. That is still "applied".
- "interview": interview scheduling links/times, confirmed interviews, or requests to schedule one.
- "offer": explicit offer or offer details.
- "rejected": explicit rejection/decline.
- Otherwise: "other".

EXTRACTION RULES:
- Never output null or empty strings. Use "(unknown)" when missing/unclear.
- Prefer role/company from SUBJECT lines and explicit patterns like “Thank you for applying to {Role} at {Company}”.
- Do NOT infer the user's role from employee signatures (“Senior SDE” in a signature ≠ the user's role).
- LOCATION is the job location (e.g., “United States (Remote)”, “Seattle, WA”). Ignore addresses in footers.
- "next_action": the most concrete action required of the candidate (e.g., “complete OA by Sep 2”, “schedule interview”); "(unknown)" if none.
- "notes": 1–2 short phrases: "application received", "OA invitation", "interview scheduled", "rejection", etc.

LLM-POWERED NORMALIZATION (must be stable across paraphrases):
- "normalized_company": lowercase, remove punctuation, strip corporate suffixes (inc, llc, corp, ltd, co), collapse whitespace. Always output the canonical company name only (e.g., "google", "dicks sporting goods", "target"). Examples:
  - "Google Inc." -> "google"
  - "Google LLC" -> "google"
  - "GOOGLE" -> "google"
- "normalized_role_title": lowercase, remove punctuation, collapse whitespace, normalize synonyms, and canonicalize token order so that cosmetic re-orderings produce the same value.
  - "TARGET" -> "target"
  - "Target Corp." -> "target"
  - "Dick's Sporting Goods" -> "dicks sporting goods"
  - "DICK'S Sporting Goods, Inc." -> "dicks sporting goods"
  - "Dick's Sporting Goods LLC" -> "dicks sporting goods"
  - Normalize: "software engineer" ~ "software engineering" ~ "swe" -> "software engineer"
  - "internship" -> "intern"
  - Keep seasonal/year tags but standardize position: use "summer 2026" form when present.
  - Examples:
    - "Software Engineering Internship (Summer 2026)" -> "software engineer intern summer 2026"
    - "2026 SWE Intern - Summer" -> "software engineer intern summer 2026"
    - "Backend Software Engineer Intern (Global E-Commerce) - 2026 Summer (BS/MS)" ->
      "backend software engineer intern summer 2026 global e commerce"

WHEN is_application = false:
- "status": "other"
- "company": best guess if clearly present, else "(unknown)"
- "role_title": "(unknown)"
- "location": "(unknown)"
- "next_action": "(ignore — non-application email)"
- "notes": brief reason ("newsletter/referral", "event/community", etc.)

OUTPUT FORMAT:
- Always return valid minified JSON (no markdown, no commentary).
""";


    public ApplicationExtractionResult extractApplication(String prompt) {
        if(apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is mandatory");
        }

        try {
            String json = callOpenAi(prompt);
            if(json == null || json.isBlank()) {
                return null;
            }
            return mapper.readValue(json, ApplicationExtractionResult.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String callOpenAi(String prompt) {
        final String traceId = UUID.randomUUID().toString().substring(0, 8);
        try {

            var messages = List.of(
                    Map.of("role", "system", "content", SYSTEM),
                    Map.of("role", "user",   "content", prompt)
            );

            var payload = new LinkedHashMap<String, Object>();
            payload.put("model", model);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", messages);

            byte[] bodyBytes = mapper.writeValueAsBytes(payload);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("[{}] OpenAI HTTP {}: {}\nrequestBodyPreview={}",
                        traceId, resp.statusCode(), resp.body(),
                        new String(bodyBytes, StandardCharsets.UTF_8)
                                .substring(0, Math.min(bodyBytes.length, 800)));
                return null;
            }

            var root = mapper.readTree(resp.body());
            String content = root.at("/choices/0/message/content").asText(null);
            if (content == null || content.isBlank()) {
                log.warn("[{}] LLM returned blank content", traceId);
                return null;
            }

            if (content.startsWith("```")) {
                int start = content.indexOf('{');
                int end   = content.lastIndexOf('}');
                if (start >= 0 && end > start) content = content.substring(start, end + 1);
            }

            // Validate it’s JSON as expected
            try {
                mapper.readTree(content);
            } catch (Exception parseEx) {
                log.error("[{}] LLM returned non-JSON despite response_format. content (trunc): {}",
                        traceId, content.length() > 500 ? content.substring(0, 500) + "…[truncated]" : content);
                return null;
            }

            return content;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("[{}] OpenAI call interrupted: {}", traceId, ie.toString());
            return null;
        } catch (Exception e) {
            log.error("[{}] OpenAI call failed: {}", traceId, e.toString(), e);
            return null;
        }
    }



    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    public static class ApplicationExtractionResult {
        @JsonProperty("is_application")
        private boolean isApplication;

        private String company;

        @JsonProperty("role_title")
        private String roleTitle;

        private String location;
        private String status;

        @JsonProperty("next_action")
        private String nextAction;

        private String notes;

        @JsonProperty("normalized_company")
        private String normalizedCompany;

        @JsonProperty("normalized_role_title")
        private String normalizedRoleTitle;
    }


}
