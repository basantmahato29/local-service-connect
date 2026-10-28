package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ProviderArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProviderAreaRepository extends JpaRepository<ProviderArea, Long> {
    List<ProviderArea> findByProviderId(Long providerId);

    @Modifying
    @Transactional
    void deleteByProviderId(Long providerId);

    @Query("""
        SELECT LOWER(TRIM(a.area)),
               COUNT(DISTINCT p.id),
               SUM(CASE WHEN p.availability = true AND p.user.enabled = true THEN 1 ELSE 0 END)
        FROM ProviderArea a, Provider p
        WHERE a.providerId = p.id
          AND p.verificationStatus = com.localserviceconnect.entity.VerificationStatus.VERIFIED
        GROUP BY LOWER(TRIM(a.area))
    """)
    List<Object[]> countVerifiedAndOnlineProvidersByArea();
}
