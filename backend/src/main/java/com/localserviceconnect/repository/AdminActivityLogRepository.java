package com.localserviceconnect.repository;

import com.localserviceconnect.entity.AdminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminActivityLogRepository extends JpaRepository<AdminActivityLog, Long>, JpaSpecificationExecutor<AdminActivityLog> {

    @Query("SELECT DISTINCT a.actionType FROM AdminActivityLog a WHERE a.actionType IS NOT NULL ORDER BY a.actionType ASC")
    List<String> findDistinctActionTypes();

    @Query("SELECT DISTINCT a.entityType FROM AdminActivityLog a WHERE a.entityType IS NOT NULL ORDER BY a.entityType ASC")
    List<String> findDistinctEntityTypes();
}

