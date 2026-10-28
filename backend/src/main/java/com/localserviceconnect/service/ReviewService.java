package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final ProviderRepository providers;
    private final NotificationService notificationService;

    @Autowired
    public ReviewService(ReviewRepository reviews, BookingRepository bookings, ProviderRepository providers,
                         NotificationService notificationService) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.providers = providers;
        this.notificationService = notificationService;
    }

    public ReviewService(ReviewRepository reviews, BookingRepository bookings, ProviderRepository providers) {
        this(reviews, bookings, providers, null);
    }

    @Transactional
    public ReviewDto create(String email, ReviewRequest r) {
        if (r == null || r.bookingId() == null || r.rating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking ID and rating are required");
        }
        if (r.rating() < 1 || r.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }

        Booking b = bookings.findById(r.bookingId()).orElseThrow(ReviewService::notFound);
        if (!b.getCustomer().getUser().getEmail().equals(email)) throw notFound(); // not your booking
        if (b.getStatus() != BookingStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can review a booking only after it is completed");
        if (reviews.existsByBookingId(b.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this booking");

        Review rv = new Review();
        rv.setBooking(b);
        rv.setCustomer(b.getCustomer());
        rv.setProvider(b.getProvider());
        rv.setRating(r.rating());
        rv.setReview(r.review() == null || r.review().isBlank() ? null : r.review().trim());
        rv.setCreatedAt(LocalDateTime.now());
        rv = reviews.saveAndFlush(rv);

        if (notificationService != null && r.rating() <= 2) {
            String provName = b.getProvider() != null && b.getProvider().getBusinessName() != null ? b.getProvider().getBusinessName() : "Provider";
            String comment = rv.getReview() != null && !rv.getReview().isBlank() ? rv.getReview() : "No text comment provided";
            notificationService.notifyAdmins(
                    "CUSTOMER_COMPLAINT",
                    "Low Rating Alert: " + r.rating() + "★ for " + provName,
                    "Customer left a " + r.rating() + "-star review for " + provName + " on booking #BK-" + b.getId() + ": \"" + comment + "\"",
                    "REVIEW",
                    rv.getId(),
                    "/admin/reviews.html",
                    "HIGH",
                    "REVIEW_LOW_" + rv.getId()
            );
        }

        recalculate(b.getProvider());
        return toDto(rv);
    }

    @Transactional(readOnly = true)
    public ReviewDto forBooking(Long bookingId) {
        return reviews.findByBookingId(bookingId).map(ReviewService::toDto).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ReviewDto> forProvider(Long providerId) {
        return reviews.findByProviderIdOrderByCreatedAtDesc(providerId).stream().map(ReviewService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminReviewDto> forCustomer(String email) {
        return reviews.findByCustomerUserEmailOrderByCreatedAtDesc(email).stream()
                .map(r -> new AdminReviewDto(r.getId(), r.getBooking().getId(), r.getCustomer().getUser().getName(),
                        r.getProvider().getBusinessName(), r.getRating(), r.getReview(), r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminReviewDto> adminList() {
        return reviews.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(r -> new AdminReviewDto(r.getId(), r.getBooking().getId(), r.getCustomer().getUser().getName(),
                        r.getProvider().getBusinessName(), r.getRating(), r.getReview(), r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminReviewDto> featured() {
        return reviews.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 4 && r.getReview() != null && !r.getReview().isBlank())
                .limit(6)
                .map(r -> new AdminReviewDto(r.getId(), r.getBooking().getId(), r.getCustomer().getUser().getName(),
                        r.getProvider().getBusinessName(), r.getRating(), r.getReview(), r.getCreatedAt())).toList();
    }

    @Transactional
    public void delete(Long id) {
        Review r = reviews.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        Provider p = r.getProvider();
        reviews.delete(r);
        reviews.flush();
        recalculate(p);
    }

    @Transactional
    public void deleteCustomerReview(Long id, String email) {
        Review r = reviews.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        if (r.getCustomer() == null || r.getCustomer().getUser() == null ||
                !r.getCustomer().getUser().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own reviews");
        }
        Provider p = r.getProvider();
        reviews.delete(r);
        reviews.flush();
        recalculate(p);
    }

    /** Provider's average and count always come from their actual reviews. */
    private void recalculate(Provider p) {
        Double avg = reviews.averageFor(p.getId());
        p.setRating(BigDecimal.valueOf(avg != null ? avg : 0.0).setScale(1, RoundingMode.HALF_UP));
        p.setReviewCount((int) reviews.countByProviderId(p.getId()));
        providers.save(p);
    }

    private static ReviewDto toDto(Review r) {
        String name = r.getCustomer().getUser().getName();
        String first = name != null && !name.isBlank() ? name.trim().split("\\s+")[0] : "Customer";
        String serviceName = (r.getBooking() != null && r.getBooking().getService() != null)
                ? r.getBooking().getService().getName() : null;
        return new ReviewDto(r.getId(), first, r.getRating(), r.getReview(),
                r.getCreatedAt() != null ? r.getCreatedAt() : LocalDateTime.now(), serviceName, true);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }
}
