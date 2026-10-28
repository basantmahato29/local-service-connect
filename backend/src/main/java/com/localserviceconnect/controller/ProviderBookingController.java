package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Provider-only booking actions (secured by /api/provider/** in SecurityConfig). */
@RestController
@RequestMapping("/api/provider/bookings")
public class ProviderBookingController {
    private final BookingService bookings;
    private final com.localserviceconnect.service.OtpService otpService;

    public ProviderBookingController(BookingService bookings, com.localserviceconnect.service.OtpService otpService) {
        this.bookings = bookings;
        this.otpService = otpService;
    }

    @PutMapping("/{id}/accept")
    public BookingDto accept(Authentication a, @PathVariable Long id) {
        return bookings.accept(id, a.getName());
    }

    @PutMapping("/{id}/reject")
    public BookingDto reject(Authentication a, @PathVariable Long id) {
        return bookings.reject(id, a.getName());
    }

    @PutMapping("/{id}/status")
    public BookingDto status(Authentication a, @PathVariable Long id, @Valid @RequestBody StatusRequest r) {
        return bookings.advance(id, a.getName(), r.status());
    }

    @PostMapping("/{id}/otp/verify")
    public BookingDto verifyOtp(Authentication a, @PathVariable Long id, @Valid @RequestBody OtpVerifyRequest r) {
        return otpService.verifyAndStart(id, a.getName(), r.otp());
    }
}
