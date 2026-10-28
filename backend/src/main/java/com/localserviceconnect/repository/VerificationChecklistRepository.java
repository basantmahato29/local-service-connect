package com.localserviceconnect.repository;

import com.localserviceconnect.entity.VerificationChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationChecklistRepository extends JpaRepository<VerificationChecklist, Long> {
    Optional<VerificationChecklist> findByApplicationId(Long applicationId);
}
