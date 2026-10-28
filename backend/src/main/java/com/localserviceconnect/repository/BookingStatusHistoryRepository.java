package com.localserviceconnect.repository;

import com.localserviceconnect.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
    List<BookingStatusHistory> findByBookingIdOrderByTimestampAsc(Long bookingId);
    List<BookingStatusHistory> findByBookingIdOrderByTimestampDesc(Long bookingId);
    List<BookingStatusHistory> findByBookingIdInOrderByBookingIdAscTimestampAsc(List<Long> bookingIds);
}

