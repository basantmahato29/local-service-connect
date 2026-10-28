package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ProviderActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderActionLogRepository extends JpaRepository<ProviderActionLog, Long> {
    List<ProviderActionLog> findByProviderIdOrderByTimestampDesc(Long providerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByProviderId(Long providerId);
}

