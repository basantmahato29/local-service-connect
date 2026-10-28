package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ServiceStatus;
import com.localserviceconnect.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    List<ServiceType> findByStatusOrderByName(ServiceStatus status);
    List<ServiceType> findAllByOrderByName();
    Optional<ServiceType> findByName(String name);
    Optional<ServiceType> findByNameIgnoreCase(String name);
    boolean existsByName(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
