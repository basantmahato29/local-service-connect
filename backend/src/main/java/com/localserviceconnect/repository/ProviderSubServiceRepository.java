package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ProviderSubService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProviderSubServiceRepository extends JpaRepository<ProviderSubService, Long> {
    List<ProviderSubService> findByProviderId(Long providerId);
    List<ProviderSubService> findByProviderIdAndEnabledTrue(Long providerId);

    @Modifying
    @Transactional
    void deleteByProviderId(Long providerId);
}
