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
        You are an information extraction system for job application emails.
        
        Return ONLY a SINGLE compact, MINIFIED JSON object with EXACTLY these keys (no extra keys, no markdown, no code fences, no explanations):
        
        {
          "is_application": boolean,
          "company": string,
          "role_title": string,
          "location": string,
          "status": "applied" | "assessment" | "interview" | "offer" | "rejected" | "other",
          "next_action": string,
          "notes": string
        }
        
        CLASSIFY FIRST:
        - Decide if this email is directly about the user's OWN job application lifecycle (any of: application received/thank you, status update, assessment/OA, interview scheduling or details, offer, rejection, candidate portal update).
        - If YES, set "is_application": true (EVEN IF some fields are unknown).
        - If NO (newsletter, referral campaign, mentorship pairing, general hiring digest, event/community/marketing, social posts, giveaways, or anything not about the user's candidacy), set "is_application": false.
        
        EXTRACTION RULES:
        - Never output nulls or empty strings. If a field is missing/unclear, use "(unknown)".
        - Prefer role/company stated in the SUBJECT or explicit lines like "Thank you for applying to {Role} at {Company}".
        - Do NOT infer the user's role from sender signatures or employee job titles (e.g., “Senior SDE” in a signature is NOT the user's role).
        - Only extract job LOCATION (e.g., "United States (Remote)", "Seattle, WA"). Ignore office addresses in footers and signature blocks.
        - If multiple roles or companies are mentioned, pick the one clearly tied to the user's candidacy. If none are clearly tied, set "is_application": false and explain briefly in "notes".
        - "status" must be one of the six allowed values. If unclear, use "other".
        - "next_action" should be the most concrete required user step (e.g., "complete OA by Aug 28", "schedule interview", "(unknown)" if none).
        - Keep "notes" brief: 1–2 short phrases summarizing what the email is (e.g., "application received", "OA invitation", "interview scheduled", "rejection", "mentorship pairing", "newsletter/referral").
        
        WHEN is_application = false:
        - Set: "status": "other"
        - Set: "company": best guess if clearly present, else "(unknown)"
        - Set: "role_title": "(unknown)"
        - Set: "location": "(unknown)"
        - Set: "next_action": "(ignore — non-application email)"
        - Set: "notes": brief reason (e.g., "newsletter/referral", "mentorship pairing", "hiring digest", "event/community")
        
        OUTPUT FORMAT:
        - Always return valid, minified JSON (no whitespace beyond what is required). No markdown. No commentary. No trailing text.
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
    }

}
