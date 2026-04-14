package com.atakant.emailtracker.service;

import com.atakant.emailtracker.config.RateLimitProperties;
import com.atakant.emailtracker.domain.DailyIngestUsage;
import com.atakant.emailtracker.repo.DailyIngestUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final DailyIngestUsageRepository dailyIngestUsageRepository;
    private final RateLimitProperties properties;

    @Transactional
    public QuotaReservation reserveProcessingQuota(UUID userId, int requestedCandidateEmails) {
        if (!properties.enabled()) {
            return QuotaReservation.disabled(requestedCandidateEmails);
        }
        if (requestedCandidateEmails <= 0) {
            return QuotaReservation.noop(properties.maxRunsPerDay(), properties.maxLlmEmailsPerDay());
        }

        LocalDate usageDate = LocalDate.now(ZoneId.of(properties.zoneId()));
        DailyIngestUsage usage = loadOrCreateUsage(userId, usageDate);

        if (usage.getRunCount() >= properties.maxRunsPerDay()) {
            return QuotaReservation.denied(
                    "Daily ingest limit reached. Try again tomorrow.",
                    Math.max(0, properties.maxRunsPerDay() - usage.getRunCount()),
                    Math.max(0, properties.maxLlmEmailsPerDay() - usage.getLlmEmailCount())
            );
        }

        int remainingDailyEmails = Math.max(0, properties.maxLlmEmailsPerDay() - usage.getLlmEmailCount());
        int allowedEmails = Math.min(requestedCandidateEmails, properties.maxLlmEmailsPerRun());
        allowedEmails = Math.min(allowedEmails, remainingDailyEmails);

        if (allowedEmails <= 0) {
            return QuotaReservation.denied(
                    "Daily OpenAI processing limit reached. Try again tomorrow.",
                    Math.max(0, properties.maxRunsPerDay() - usage.getRunCount()),
                    remainingDailyEmails
            );
        }

        usage.setRunCount(usage.getRunCount() + 1);
        usage.setLlmEmailCount(usage.getLlmEmailCount() + allowedEmails);
        usage.setUpdatedAt(Instant.now());
        dailyIngestUsageRepository.save(usage);

        log.info("Reserved daily ingest quota userId={} date={} allowedEmails={} requestedEmails={} runs={}/{} llmEmails={}/{}",
                userId,
                usageDate,
                allowedEmails,
                requestedCandidateEmails,
                usage.getRunCount(),
                properties.maxRunsPerDay(),
                usage.getLlmEmailCount(),
                properties.maxLlmEmailsPerDay());

        return QuotaReservation.allowed(
                allowedEmails,
                allowedEmails < requestedCandidateEmails,
                Math.max(0, properties.maxRunsPerDay() - usage.getRunCount()),
                Math.max(0, properties.maxLlmEmailsPerDay() - usage.getLlmEmailCount())
        );
    }

    private DailyIngestUsage loadOrCreateUsage(UUID userId, LocalDate usageDate) {
        DailyIngestUsage existing = dailyIngestUsageRepository.findForUpdate(userId, usageDate).orElse(null);
        if (existing != null) {
            return existing;
        }

        DailyIngestUsage created = DailyIngestUsage.builder()
                .userId(userId)
                .usageDate(usageDate)
                .runCount(0)
                .llmEmailCount(0)
                .build();

        try {
            return dailyIngestUsageRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException race) {
            return dailyIngestUsageRepository.findForUpdate(userId, usageDate)
                    .orElseThrow(() -> race);
        }
    }

    public record QuotaReservation(
            boolean allowed,
            int allowedCandidateEmails,
            boolean truncated,
            int remainingRunsToday,
            int remainingLlmEmailsToday,
            String message
    ) {
        static QuotaReservation allowed(int allowedCandidateEmails,
                                        boolean truncated,
                                        int remainingRunsToday,
                                        int remainingLlmEmailsToday) {
            return new QuotaReservation(
                    true,
                    allowedCandidateEmails,
                    truncated,
                    remainingRunsToday,
                    remainingLlmEmailsToday,
                    truncated
                            ? "Only part of this inbox batch can be processed within today's quota."
                            : "Quota reserved."
            );
        }

        static QuotaReservation denied(String message,
                                       int remainingRunsToday,
                                       int remainingLlmEmailsToday) {
            return new QuotaReservation(false, 0, false, remainingRunsToday, remainingLlmEmailsToday, message);
        }

        static QuotaReservation disabled(int requestedCandidateEmails) {
            return new QuotaReservation(true, requestedCandidateEmails, false, Integer.MAX_VALUE, Integer.MAX_VALUE,
                    "Rate limiting disabled.");
        }

        static QuotaReservation noop(int remainingRunsToday, int remainingLlmEmailsToday) {
            return new QuotaReservation(true, 0, false, remainingRunsToday, remainingLlmEmailsToday,
                    "No candidate emails to process.");
        }
    }
}
