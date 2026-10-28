package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.Provider;
import com.localserviceconnect.repository.ProviderRepository;
import com.localserviceconnect.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class ReviewController {

    private final ReviewService reviewService;
    private final ProviderRepository providerRepository;

    public ReviewController(ReviewService reviewService, ProviderRepository providerRepository) {
        this.reviewService = reviewService;
        this.providerRepository = providerRepository;
    }

    @PostMapping("/api/reviews")
    public ResponseEntity<ReviewDto> create(
            Authentication authentication,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(reviewService.create(authentication.getName(), request));
    }

    @GetMapping("/api/reviews/booking/{bookingId}")
    public ResponseEntity<ReviewDto> getForBooking(@PathVariable Long bookingId) {
        ReviewDto review = reviewService.forBooking(bookingId);
        if (review == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(review);
    }

    @GetMapping("/api/reviews/provider/{providerId}")
    public ResponseEntity<List<ReviewDto>> getForProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(reviewService.forProvider(providerId));
    }

    @GetMapping("/api/reviews/featured")
    public ResponseEntity<List<AdminReviewDto>> getFeaturedReviews() {
        return ResponseEntity.ok(reviewService.featured());
    }

    @GetMapping("/api/reviews")
    public ResponseEntity<List<ReviewDto>> getReviews(@RequestParam(required = false) Long providerId) {
        if (providerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerId parameter is required");
        }
        return ResponseEntity.ok(reviewService.forProvider(providerId));
    }

    @GetMapping("/api/customer/reviews")
    public ResponseEntity<List<AdminReviewDto>> getMyCustomerReviews(Authentication authentication) {
        return ResponseEntity.ok(reviewService.forCustomer(authentication.getName()));
    }

    @DeleteMapping("/api/customer/reviews/{id}")
    public ResponseEntity<Void> deleteMyReview(@PathVariable Long id, Authentication authentication) {
        reviewService.deleteCustomerReview(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/provider/reviews")
    public ResponseEntity<List<ReviewDto>> getMyProviderReviews(Authentication authentication) {
        Provider p = providerRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        return ResponseEntity.ok(reviewService.forProvider(p.getId()));
    }
}
