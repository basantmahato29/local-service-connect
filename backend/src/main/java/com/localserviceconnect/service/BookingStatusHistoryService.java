package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.BookingStatusHistoryDto;
import com.localserviceconnect.entity.BookingStatusHistory;
import com.localserviceconnect.repository.BookingStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingStatusHistoryService {

    private final BookingStatusHistoryRepository historyRepo;

    public BookingStatusHistoryService(BookingStatusHistoryRepository historyRepo) {
        this.historyRepo = historyRepo;
    }

    @Transactional
    public void record(Long bookingId, String fromStatus, String toStatus, String action,
                       String reason, Long actorId, String actorName, String actorRole) {
        try {
            BookingStatusHistory h = new BookingStatusHistory(
                    bookingId, fromStatus, toStatus, action, reason, actorId, actorName, actorRole
            );
            historyRepo.save(h);
        } catch (Exception ignored) {
        }
    }

    @Transactional(readOnly = true)
    public List<BookingStatusHistoryDto> getHistoryForBooking(Long bookingId) {
        return historyRepo.findByBookingIdOrderByTimestampAsc(bookingId)
                .stream().map(h -> new BookingStatusHistoryDto(
                        h.getId(),
                        h.getBookingId(),
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getAction(),
                        h.getReason(),
                        h.getActorId(),
                        h.getActorName(),
                        h.getActorRole(),
                        h.getTimestamp()
                )).toList();
    }
}

