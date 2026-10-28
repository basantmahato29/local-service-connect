package com.localserviceconnect.repository;

import com.localserviceconnect.entity.VerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationHistoryRepository extends JpaRepository<VerificationHistory, Long> {
    List<VerificationHistory> findByApplicationIdOrderByTimestampAsc(Long applicationId);
}
