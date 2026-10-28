package com.localserviceconnect.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "provider_action_logs")
public class ProviderActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(nullable = false, length = 50)
    private String action; // e.g. SUSPEND, REINSTATE, PROFILE_EDIT, MANUAL_VERIFY

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public ProviderActionLog() {}

    public ProviderActionLog(Long providerId, String action, String reason, Long actorId, String actorName, String actorRole) {
        this.providerId = providerId;
        this.action = action;
        this.reason = reason;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}

