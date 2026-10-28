package com.localserviceconnect.repository;

import com.localserviceconnect.entity.SubService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubServiceRepository extends JpaRepository<SubService, Long> {
    List<SubService> findByServiceIdOrderByDisplayOrderAscNameAsc(Long serviceId);
    List<SubService> findByServiceIdAndEnabledTrueOrderByDisplayOrderAscNameAsc(Long serviceId);
    List<SubService> findByEnabledTrueOrderByDisplayOrderAscNameAsc();
    Optional<SubService> findByServiceIdAndNameIgnoreCase(Long serviceId, String name);
    boolean existsByServiceIdAndNameIgnoreCase(Long serviceId, String name);
    boolean existsByServiceIdAndNameIgnoreCaseAndIdNot(Long serviceId, String name, Long id);
}
