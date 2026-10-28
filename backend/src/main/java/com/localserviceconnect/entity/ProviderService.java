package com.localserviceconnect.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "provider_services", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider_id", "service_id"})
})
public class ProviderService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    public ProviderService() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
}
