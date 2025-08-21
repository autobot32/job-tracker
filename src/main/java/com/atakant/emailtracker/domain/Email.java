package com.atakant.emailtracker.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emails")
@Getter @Setter @ToString
@NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id")
public class Email {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID(); // app-generated UUID

    @Column(name = "user_id", nullable = false)
    private UUID userId;   // FK to users

    @Column(name = "thread_id", nullable = false)
    private String threadId;

    @Column(name = "message_id_hash", nullable = false)
    private String messageIdHash; // sha256(userId + "|" + (RFC822 Message-ID or gmail_id))

    @Column(name = "from_addr")
    private String fromAddr;

    @Column(name = "to_addr")
    private String toAddr;

    private String subject;

    @Column(name = "sent_at")
    private Instant sentAt; // TIMESTAMPTZ <-> Instant

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "raw_label")
    private String rawLabel;

    @Column(name = "llm_type")
    private String llmType;

    // V2 additions you made
    @Column(name = "gmail_id")
    private String gmailId;

    @Column(name = "internal_date_ms")
    private Long internalDateMs;

    @PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }
}
