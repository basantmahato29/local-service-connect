package com.localserviceconnect.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_activity_logs", indexes = {
    @Index(name = "idx_actlog_timestamp", columnList = "timestamp"),
    @Index(name = "idx_actlog_action_type", columnList = "action_type"),
    @Index(name = "idx_actlog_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_actlog_actor", columnList = "actor_id")
})
public class AdminActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "action_type", nullable = false, length = 60)
    private String actionType; // PROVIDER_APPROVED, PROVIDER_REJECTED, PROVIDER_SUSPENDED, etc.

    @Column(name = "entity_type", length = 50)
    private String entityType; // PROVIDER, BOOKING, SERVICE, REVIEW, VERIFICATION_APP, SYSTEM

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_identifier", length = 150)
    private String entityIdentifier;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "result", length = 20, nullable = false)
    private String result = "SUCCESS";

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "target_url", length = 255)
    private String targetUrl;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AdminActivityLog() {}

    public AdminActivityLog(Long actorId, String actorName, String actorEmail, String actorRole,
                            String actionType, String entityType, Long entityId, String entityIdentifier,
                            String details, String reason, String result, String ipAddress, String targetUrl) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityIdentifier = entityIdentifier;
        this.details = details;
        this.reason = reason;
        this.result = result != null ? result : "SUCCESS";
        this.ipAddress = ipAddress;
        this.targetUrl = targetUrl;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getEntityIdentifier() { return entityIdentifier; }
    public void setEntityIdentifier(String entityIdentifier) { this.entityIdentifier = entityIdentifier; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}

