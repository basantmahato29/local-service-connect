package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ProviderPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProviderPortfolioRepository extends JpaRepository<ProviderPortfolio, Long> {
    List<ProviderPortfolio> findByProviderIdOrderByIdAsc(Long providerId);

    @Modifying
    @Transactional
    void deleteByProviderId(Long providerId);
}
