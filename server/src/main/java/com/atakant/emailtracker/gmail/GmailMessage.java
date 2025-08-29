package com.atakant.emailtracker.gmail;

import java.time.OffsetDateTime;
import java.util.List;

public record GmailMessage(String gmailId,
                           String threadId,
                           String rfc822MessageId,      // may be null; we’ll fall back to gmailId
                           long internalDateMs,
                           String from,
                           String to,
                           String subject,
                           OffsetDateTime sentAtUtc,    // convert to UTC
                           String bodyText,
                           List<String> labels) {
}
