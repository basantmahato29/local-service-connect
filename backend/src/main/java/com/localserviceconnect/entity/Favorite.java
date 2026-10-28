package com.localserviceconnect.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "customer_favorites",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_provider_favorite", columnNames = {"customer_id", "provider_id"})
    },
    indexes = {
        @Index(name = "idx_fav_customer", columnList = "customer_id"),
        @Index(name = "idx_fav_provider", columnList = "provider_id")
    }
)
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "notify_on_available", nullable = false)
    private boolean notifyOnAvailable = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Favorite() {}

    public Favorite(Customer customer, Provider provider) {
        this.customer = customer;
        this.provider = provider;
        this.notifyOnAvailable = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public boolean isNotifyOnAvailable() {
        return notifyOnAvailable;
    }

    public void setNotifyOnAvailable(boolean notifyOnAvailable) {
        this.notifyOnAvailable = notifyOnAvailable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
