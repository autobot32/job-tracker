package com.atakant.emailtracker.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "applications")
public class Application {

  @Id
  private UUID id = UUID.randomUUID();

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private String company;

  @Column(nullable = false)
  private String roleTitle;

  private String location;

  private OffsetDateTime firstSeenAt;
  private String status; // enum-as-string for MVP
  private String nextStep;
  private LocalDate dueDate;
  private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();
  private String notes;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getCompany() { return company; }
  public void setCompany(String company) { this.company = company; }
  public String getRoleTitle() { return roleTitle; }
  public void setRoleTitle(String roleTitle) { this.roleTitle = roleTitle; }
  public String getLocation() { return location; }
  public void setLocation(String location) { this.location = location; }
  public OffsetDateTime getFirstSeenAt() { return firstSeenAt; }
  public void setFirstSeenAt(OffsetDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getNextStep() { return nextStep; }
  public void setNextStep(String nextStep) { this.nextStep = nextStep; }
  public LocalDate getDueDate() { return dueDate; }
  public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
  public OffsetDateTime getLastUpdatedAt() { return lastUpdatedAt; }
  public void setLastUpdatedAt(OffsetDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
  public String getNotes() { return notes; }
  public void setNotes(String notes) { this.notes = notes; }
}
