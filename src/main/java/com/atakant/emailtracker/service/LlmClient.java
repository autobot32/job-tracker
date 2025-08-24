package com.atakant.emailtracker.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
public class LlmClient {

    private final ObjectMapper mapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public ApplicationExtractionResult extractApplication(String prompt) {
        // Before LLM is hooked up, example JSON provided
        String json = """
          {"company":"Figma",
           "role_title":"Software Engineer Intern",
           "location":"San Francisco, CA",
           "status":"applied",
           "next_action":"Complete coding challenge by 2025-09-01",
           "notes":"Referred by Jane D."
          }
        """;

        try {
            return mapper.readValue(json, ApplicationExtractionResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Stub JSON failed to parse", e);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApplicationExtractionResult {
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
