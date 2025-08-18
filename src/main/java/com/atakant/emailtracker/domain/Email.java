package com.atakant.emailtracker.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "emails",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "gmail_id"})
        })
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Email {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    private UUID id; // PK


    @Column(name = "user_id", nullable = false)
    private UUID userId;   // FK to users

    @Column(name = "thread_id")
    private String threadId;

    @Column(name = "message_id_hash")
    private String messageIdHash;

    @Column(name = "from_addr")
    private String fromAddr;

    @Column(name = "to_addr")
    private String toAddr;

    private String subject;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "raw_label")
    private String rawLabel;

    @Column(name = "llm_type")
    private String llmType;

    @Column(name = "gmail_id")
    private String gmailId;

    @Column(name = "internal_date_ms")
    private Long internalDateMs;
}
