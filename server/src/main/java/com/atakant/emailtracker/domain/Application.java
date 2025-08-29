package com.atakant.emailtracker.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

  @Id
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String company;

  @Column(name = "role_title", nullable = false)
  private String roleTitle;

  private String location;

  @Column(name = "source_email_id")
  private UUID sourceEmailId;

  @Column(name = "first_seen_at", nullable = false)
  private OffsetDateTime firstSeenAt;

  @Column(nullable = false)
  private String status; // enum-as-string for MVP

  @Column(name = "next_step")
  private String nextStep;

  @Column(name = "next_due_at")
  private OffsetDateTime nextDueAt;

  @Column(name = "last_updated_at", nullable = false)
  private OffsetDateTime lastUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);

  private String notes;

  @Column(name = "is_application", nullable = false)
  private boolean isApplication = true;

  @Column(name = "normalized_company")
  private String normalizedCompany;

  @Column(name = "normalized_role_title")
  private String normalizedRoleTitle;

  @PrePersist
  void onCreate() {
    if (id == null) id = UUID.randomUUID();
    if (firstSeenAt == null) firstSeenAt = OffsetDateTime.now(ZoneOffset.UTC);
    if (lastUpdatedAt == null) lastUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  @PreUpdate
  void onUpdate() {
    lastUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}
