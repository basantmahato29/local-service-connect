package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final com.localserviceconnect.service.OtpService otpService;

    public BookingController(BookingService bookingService, com.localserviceconnect.service.OtpService otpService) {
        this.bookingService = bookingService;
        this.otpService = otpService;
    }

    @PostMapping
    public ResponseEntity<BookingDto> create(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody BookingRequest request) {
        return ResponseEntity.ok(bookingService.create(authentication.getName(), request));
    }

    @GetMapping("/my")
    public ResponseEntity<List<BookingDto>> my(Authentication authentication) {
        String role = extractRole(authentication);
        return ResponseEntity.ok(bookingService.my(authentication.getName(), role));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingDto> get(
            @PathVariable Long id,
            Authentication authentication) {
        String role = extractRole(authentication);
        return ResponseEntity.ok(bookingService.get(id, authentication.getName(), role));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<BookingDto> cancel(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(bookingService.cancel(id, authentication.getName()));
    }

    @PostMapping("/{id}/otp/generate")
    public ResponseEntity<OtpResponse> generateOtp(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(otpService.generate(id, authentication.getName()));
    }

    @GetMapping("/{id}/otp")
    public ResponseEntity<OtpResponse> getOtp(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(otpService.getOtpForCustomer(id, authentication.getName()));
    }

    @PostMapping("/{id}/otp/verify")
    public ResponseEntity<BookingDto> verifyOtp(
            @PathVariable Long id,
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(otpService.verifyAndStart(id, authentication.getName(), request.otp()));
    }

    @PostMapping("/{id}/completion/confirm")
    public ResponseEntity<BookingDto> confirmCompletion(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(otpService.confirmCompletion(id, authentication.getName()));
    }

    @GetMapping("/eligible-review")
    public ResponseEntity<BookingDto> getEligibleReview(
            @RequestParam Long providerId,
            Authentication authentication) {
        if (authentication == null) return ResponseEntity.noContent().build();
        return bookingService.getEligibleReviewBooking(authentication.getName(), providerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }


    private String extractRole(Authentication authentication) {
        if (authentication == null) return "";
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("");
    }
}
