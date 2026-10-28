package com.localserviceconnect.repository;

import com.localserviceconnect.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email);

    Page<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email, Pageable pageable);

    long countByRecipientEmailAndReadFalse(String email);

    boolean existsByRecipientIdAndBookingIdAndType(Long recipientId, Long bookingId, String type);

    boolean existsByRecipientIdAndDedupKey(Long recipientId, String dedupKey);

    Optional<Notification> findFirstByRecipientEmailAndTypeAndBookingStatusOrderByCreatedAtDesc(String email, String type, String bookingStatus);

    List<Notification> findByBookingId(Long bookingId);

    @Modifying
    @Transactional
    void deleteByRecipientId(Long recipientId);
}
