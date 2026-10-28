package com.localserviceconnect.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "provider_portfolio")
public class ProviderPortfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "sub_service", length = 100)
    private String subService;

    @Column(name = "before_image_url", length = 255)
    private String beforeImageUrl;

    @Column(name = "after_image_url", length = 255)
    private String afterImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "completed_date", length = 50)
    private String completedDate;

    public ProviderPortfolio() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubService() { return subService; }
    public void setSubService(String subService) { this.subService = subService; }

    public String getBeforeImageUrl() { return beforeImageUrl; }
    public void setBeforeImageUrl(String beforeImageUrl) { this.beforeImageUrl = beforeImageUrl; }

    public String getAfterImageUrl() { return afterImageUrl; }
    public void setAfterImageUrl(String afterImageUrl) { this.afterImageUrl = afterImageUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCompletedDate() { return completedDate; }
    public void setCompletedDate(String completedDate) { this.completedDate = completedDate; }
}
