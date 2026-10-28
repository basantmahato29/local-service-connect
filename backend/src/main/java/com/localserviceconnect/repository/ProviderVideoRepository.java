package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ProviderVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProviderVideoRepository extends JpaRepository<ProviderVideo, Long> {
    List<ProviderVideo> findByProviderIdOrderByIdAsc(Long providerId);

    @Modifying
    @Transactional
    void deleteByProviderId(Long providerId);
}
