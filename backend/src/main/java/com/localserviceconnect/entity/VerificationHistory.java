package com.localserviceconnect.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_history")
public class VerificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private VerificationApplication application;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private ApplicationStatus toStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public VerificationHistory() {}

    public VerificationHistory(VerificationApplication application, ApplicationStatus fromStatus, ApplicationStatus toStatus, User actor, String action, String notes) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        if (actor != null) {
            try {
                this.actorName = actor.getName();
                this.actorRole = actor.getRole() != null ? actor.getRole().name() : "USER";
            } catch (Exception ignored) {}
        }
        this.action = action;
        this.notes = notes;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public VerificationApplication getApplication() { return application; }
    public void setApplication(VerificationApplication application) { this.application = application; }

    public ApplicationStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ApplicationStatus fromStatus) { this.fromStatus = fromStatus; }

    public ApplicationStatus getToStatus() { return toStatus; }
    public void setToStatus(ApplicationStatus toStatus) { this.toStatus = toStatus; }

    public User getActor() { return actor; }
    public void setActor(User actor) {
        this.actor = actor;
        if (actor != null) {
            try {
                this.actorName = actor.getName();
                this.actorRole = actor.getRole() != null ? actor.getRole().name() : "USER";
            } catch (Exception ignored) {}
        }
    }

    public String getActorName() {
        if (actorName != null) return actorName;
        try {
            if (actor != null) return actor.getName();
        } catch (Exception ignored) {}
        return "System";
    }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorRole() {
        if (actorRole != null) return actorRole;
        try {
            if (actor != null && actor.getRole() != null) return actor.getRole().name();
        } catch (Exception ignored) {}
        return "SYSTEM";
    }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
