package com.localserviceconnect.repository;

import com.localserviceconnect.entity.Booking;
import com.localserviceconnect.entity.BookingStatus;
import com.localserviceconnect.entity.BookingType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findWithLockById(@Param("id") Long id);

    boolean existsByCustomerIdAndProviderIdAndStatus(Long customerId, Long providerId, BookingStatus status);
    List<Booking> findByProviderUserEmailOrderByCreatedAtDesc(String email);
    List<Booking> findByCustomerUserEmailOrderByCreatedAtDesc(String email);

    boolean existsByProviderIdAndStatusIn(Long providerId, List<BookingStatus> statuses);
    boolean existsByProviderIdAndStatusAndBookingType(Long providerId, BookingStatus status, BookingType bookingType);

    long countByStatus(BookingStatus status);
    long countByBookingType(BookingType bookingType);
    long countByCustomerId(Long customerId);
    long countByProviderId(Long providerId);
    long countByProviderIdAndStatus(Long providerId, BookingStatus status);
    long countByProviderIdAndStatusIn(Long providerId, List<BookingStatus> statuses);
    List<Booking> findByProviderIdAndStatusInOrderByIdDesc(Long providerId, List<BookingStatus> statuses);
    List<Booking> findByBookingTypeAndStatusInOrderByIdDesc(BookingType bookingType, List<BookingStatus> statuses);
    List<Booking> findByCustomerIdAndProviderIdAndStatus(Long customerId, Long providerId, BookingStatus status);
    boolean existsByServiceId(Long serviceId);
}

