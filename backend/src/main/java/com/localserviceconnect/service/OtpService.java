package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.Booking;
import com.localserviceconnect.entity.BookingStatus;
import com.localserviceconnect.entity.ServiceOtp;
import com.localserviceconnect.repository.BookingRepository;
import com.localserviceconnect.repository.ServiceOtpRepository;
import com.localserviceconnect.security.CryptoUtil;
import com.localserviceconnect.service.sms.SmsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final BookingRepository bookings;
    private final ServiceOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final CryptoUtil cryptoUtil;
    private final SmsService smsService;
    private final BookingService bookingService;
    private final BookingStatusHistoryService historyService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-minutes:10}")
    private int expirationMinutes = 10;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts = 5;

    @Value("${app.otp.lockout-minutes:15}")
    private int lockoutMinutes = 15;

    // Rate limiter: sliding window tracking per booking ID
    private final Map<Long, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    public OtpService(
            BookingRepository bookings,
            ServiceOtpRepository otps,
            PasswordEncoder passwordEncoder,
            CryptoUtil cryptoUtil,
            SmsService smsService,
            @Lazy BookingService bookingService,
            BookingStatusHistoryService historyService) {
        this.bookings = bookings;
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.cryptoUtil = cryptoUtil;
        this.smsService = smsService;
        this.bookingService = bookingService;
        this.historyService = historyService;
    }

    /**
     * Generates or regenerates a cryptographically secure 4-digit OTP for an accepted booking.
     */
    @Transactional
    public OtpResponse generate(Long bookingId, String customerEmail) {
        Booking b = bookings.findById(bookingId).orElseThrow(OtpService::notFound);
        if (!b.getCustomer().getUser().getEmail().equals(customerEmail)) {
            throw notFound();
        }

        if (b.getStatus() == BookingStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OTP can only be generated after the provider accepts the booking");
        }
        if (b.getStatus() == BookingStatus.SERVICE_STARTED || b.getStatus() == BookingStatus.AWAITING_COMPLETION || b.getStatus() == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service has already been started or completed for this booking");
        }
        if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot generate OTP for a " + b.getStatus() + " booking");
        }

        return createOrUpdateOtp(b);
    }

    /**
     * Internal method to create or regenerate OTP (called when provider accepts, or when customer regenerates).
     */
    @Transactional
    public OtpResponse createOrUpdateOtp(Booking b) {
        ServiceOtp otp = otps.findByBookingId(b.getId()).orElseGet(() -> {
            ServiceOtp o = new ServiceOtp();
            o.setBooking(b);
            return o;
        });

        if (otp.isVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OTP for this booking has already been verified");
        }

        // Generate cryptographically secure 4-digit number
        String plainOtp = String.format("%04d", secureRandom.nextInt(10_000));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(expirationMinutes);

        // Store secure one-way hash (BCrypt) for verification
        otp.setOtpHash(passwordEncoder.encode(plainOtp));
        // Encrypt with AES-GCM for secure customer retrieval
        otp.setEncryptedOtp(cryptoUtil.encrypt(plainOtp));
        otp.setCreatedAt(now);
        otp.setExpiresAt(expiresAt);
        otp.setAttemptCount(0);
        otp.setMaxAttempts(maxAttempts);
        otp.setVerified(false);
        otp.setVerifiedAt(null);
        otp.setBlockedUntil(null);

        otps.save(otp);

        // Optional external SMS notification
        String customerPhone = b.getCustomer().getUser().getPhone();
        String customerName = b.getCustomer().getUser().getName();
        smsService.sendOtp(customerPhone, plainOtp, customerName);

        long remainingSeconds = Math.max(0, Duration.between(now, expiresAt).getSeconds());
        return new OtpResponse(
                plainOtp,
                expiresAt,
                remainingSeconds,
                false,
                false,
                "Share this OTP with your service provider only after they arrive and you verify their identity."
        );
    }

    /**
     * Customer retrieves the active OTP for their booking details page.
     * Plaintext OTP is NEVER exposed to providers, admins, or non-owners.
     */
    @Transactional
    public OtpResponse getOtpForCustomer(Long bookingId, String customerEmail) {
        Booking b = bookings.findById(bookingId).orElseThrow(OtpService::notFound);
        if (!b.getCustomer().getUser().getEmail().equals(customerEmail)) {
            throw notFound();
        }

        if (b.getStatus() == BookingStatus.PENDING) {
            return new OtpResponse(null, null, 0, false, false, "Booking request is pending provider acceptance.");
        }
        if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) {
            return new OtpResponse(null, null, 0, false, false, "Booking is " + b.getStatus() + ".");
        }

        var opt = otps.findByBookingId(bookingId);
        if (opt.isEmpty()) {
            if (b.getStatus() == BookingStatus.ACCEPTED || b.getStatus() == BookingStatus.ON_THE_WAY) {
                // Auto-generate if missing for accepted booking
                return createOrUpdateOtp(b);
            }
            return new OtpResponse(null, null, 0, true, false, "Service started or completed.");
        }

        ServiceOtp otp = opt.get();
        if (otp.isVerified() || b.getStatus() == BookingStatus.SERVICE_STARTED || b.getStatus() == BookingStatus.AWAITING_COMPLETION || b.getStatus() == BookingStatus.COMPLETED) {
            return new OtpResponse(null, otp.getExpiresAt(), 0, true, false, "Service has already been verified and started.");
        }

        if (otp.isExpired()) {
            return new OtpResponse(null, otp.getExpiresAt(), 0, false, true, "OTP has expired. Please click below to generate a new OTP.");
        }

        // Decrypt ciphertext for authenticated customer display
        String plainOtp = cryptoUtil.decrypt(otp.getEncryptedOtp());
        long remaining = Math.max(0, Duration.between(LocalDateTime.now(), otp.getExpiresAt()).getSeconds());
        return new OtpResponse(
                plainOtp,
                otp.getExpiresAt(),
                remaining,
                false,
                false,
                "Share this OTP with your service provider only after they arrive and you verify their identity."
        );
    }

    /**
     * Provider submits the 4-digit customer OTP to verify and start the service.
     * Uses pessimistic write locking to prevent concurrent double-verification.
     */
    @Transactional
    public BookingDto verifyAndStart(Long bookingId, String providerEmail, String inputOtp) {
        if (inputOtp == null || !inputOtp.trim().matches("^[0-9]{4}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid 4-digit OTP");
        }

        // Pessimistic lock on booking
        Booking b = bookings.findWithLockById(bookingId).orElseThrow(OtpService::notFound);
        if (!b.getProvider().getUser().getEmail().equals(providerEmail)) {
            throw notFound();
        }

        if (b.getStatus() == BookingStatus.SERVICE_STARTED || b.getStatus() == BookingStatus.AWAITING_COMPLETION || b.getStatus() == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service has already been started or completed");
        }
        if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot start service for a " + b.getStatus() + " booking");
        }
        if (b.getStatus() != BookingStatus.ACCEPTED && b.getStatus() != BookingStatus.ON_THE_WAY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking must be accepted before service can be started");
        }

        // Rate limit verification attempts (max 10 requests per minute per booking)
        RateLimiter limiter = rateLimiters.computeIfAbsent(bookingId, k -> new RateLimiter(10, 60_000));
        if (!limiter.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many verification requests. Please wait a moment and try again.");
        }

        // Lock OTP record
        ServiceOtp otp = otps.findWithLockByBookingId(bookingId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "No OTP found for this booking. Ask customer to generate an OTP."));

        if (otp.isVerified()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This OTP has already been verified and service has started");
        }

        if (otp.isBlocked()) {
            long remainingMins = Math.max(1, Duration.between(LocalDateTime.now(), otp.getBlockedUntil()).toMinutes());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Verification is blocked for " + remainingMins + " more minute(s). Ask the customer to generate a new OTP.");
        }

        if (otp.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired. Please ask the customer to generate a new OTP.");
        }

        // Verify using BCrypt hash comparison
        boolean match = passwordEncoder.matches(inputOtp.trim(), otp.getOtpHash());
        if (!match) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            int remaining = Math.max(0, otp.getMaxAttempts() - otp.getAttemptCount());

            if (remaining == 0) {
                otp.setBlockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
                otps.save(otp);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Incorrect OTP. Maximum attempts reached (" + otp.getMaxAttempts() + "/" + otp.getMaxAttempts() + "). Verification temporarily blocked. Ask the customer to generate a new OTP.");
            }

            otps.save(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Incorrect OTP. " + remaining + " attempt(s) remaining.");
        }

        // Successful verification: transition to SERVICE_STARTED and invalidate OTP
        otp.setVerified(true);
        otp.setVerifiedAt(LocalDateTime.now());
        otp.setEncryptedOtp(null); // Wipe encrypted plaintext permanently
        otps.save(otp);

        BookingStatus prev = b.getStatus();
        b.setStatus(BookingStatus.SERVICE_STARTED);
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), prev.name(), BookingStatus.SERVICE_STARTED.name(), "START_SERVICE",
                "Customer OTP verified on-site; service started",
                b.getProvider().getUser().getId(), b.getProvider().getUser().getName(), "PROVIDER");
        return bookingService.toDto(saved, "PROVIDER");
    }

    /**
     * Customer confirms service completion.
     */
    @Transactional
    public BookingDto confirmCompletion(Long bookingId, String customerEmail) {
        Booking b = bookings.findWithLockById(bookingId).orElseThrow(OtpService::notFound);
        if (!b.getCustomer().getUser().getEmail().equals(customerEmail)) {
            throw notFound();
        }

        if (b.getStatus() == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking has already been completed");
        }
        if (b.getStatus() != BookingStatus.SERVICE_STARTED && b.getStatus() != BookingStatus.AWAITING_COMPLETION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service completion can only be confirmed after the service has started (current status: " + b.getStatus() + ")");
        }

        BookingStatus prev = b.getStatus();
        b.setStatus(BookingStatus.COMPLETED);
        b.setCompletedAt(LocalDateTime.now());
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), prev.name(), BookingStatus.COMPLETED.name(), "CONFIRM_COMPLETION",
                "Customer verified fulfillment and confirmed completion",
                b.getCustomer().getUser().getId(), b.getCustomer().getUser().getName(), "CUSTOMER");
        return bookingService.toDto(saved, "CUSTOMER");
    }

    @Transactional
    public void invalidateForBooking(Long bookingId) {
        otps.deleteByBookingId(bookingId);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }

    /**
     * Simple thread-safe sliding window rate limiter
     */
    private static class RateLimiter {
        private final int maxCalls;
        private final long windowMillis;
        private final java.util.Queue<Long> callTimestamps = new java.util.concurrent.ConcurrentLinkedQueue<>();

        public RateLimiter(int maxCalls, long windowMillis) {
            this.maxCalls = maxCalls;
            this.windowMillis = windowMillis;
        }

        public synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            while (!callTimestamps.isEmpty() && now - callTimestamps.peek() > windowMillis) {
                callTimestamps.poll();
            }
            if (callTimestamps.size() < maxCalls) {
                callTimestamps.add(now);
                return true;
            }
            return false;
        }
    }
}

