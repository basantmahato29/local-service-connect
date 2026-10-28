package com.localserviceconnect.repository;

import com.localserviceconnect.entity.ServiceOtp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ServiceOtpRepository extends JpaRepository<ServiceOtp, Long> {

    Optional<ServiceOtp> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM ServiceOtp o WHERE o.booking.id = :bookingId")
    Optional<ServiceOtp> findWithLockByBookingId(@Param("bookingId") Long bookingId);

    void deleteByBookingId(Long bookingId);
}

