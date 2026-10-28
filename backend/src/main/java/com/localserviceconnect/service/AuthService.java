package com.localserviceconnect.service;

import com.localserviceconnect.dto.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final CustomerRepository customers;
    private final ProviderRepository providers;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;
    private final NotificationService notificationService;

    @Autowired
    public AuthService(UserRepository users, CustomerRepository customers, ProviderRepository providers,
                       PasswordEncoder encoder, JwtUtil jwt, NotificationService notificationService) {
        this.users = users;
        this.customers = customers;
        this.providers = providers;
        this.encoder = encoder;
        this.jwt = jwt;
        this.notificationService = notificationService;
    }

    public AuthService(UserRepository users, CustomerRepository customers, ProviderRepository providers,
                       PasswordEncoder encoder, JwtUtil jwt) {
        this(users, customers, providers, encoder, jwt, null);
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        if (r.role() == Role.ADMIN)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        if (r.role() == Role.PROVIDER && (r.businessName() == null || r.businessName().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business name is required for providers");

        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPhone(r.phone());
        u.setPassword(encoder.encode(r.password()));
        u.setRole(r.role());
        users.save(u);

        if (r.role() == Role.CUSTOMER) {
            Customer c = new Customer();
            c.setUser(u);
            customers.save(c);
        } else { // providers start as PENDING and unavailable until an admin verifies them
            Provider p = new Provider();
            p.setUser(u);
            p.setBusinessName(r.businessName().trim());
            Provider savedProvider = providers.save(p);

            if (notificationService != null) {
                notificationService.notifyAdmins(
                        "NEW_PROVIDER_REGISTRATION",
                        "New Provider Registered: " + p.getBusinessName(),
                        "Provider " + u.getName() + " registered with business name '" + p.getBusinessName() + "'. Awaiting onboarding and verification.",
                        "PROVIDER",
                        savedProvider.getId(),
                        "/admin/providers.html?search=" + java.net.URLEncoder.encode(p.getBusinessName(), java.nio.charset.StandardCharsets.UTF_8),
                        "NORMAL",
                        "PROV_REG_" + savedProvider.getId()
                );
            }
        }
        return response(u);
    }

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 minutes
    private static final java.util.Map<String, LoginAttemptTracker> loginAttempts = new java.util.concurrent.ConcurrentHashMap<>();

    private static class LoginAttemptTracker {
        private int failedAttempts = 0;
        private long lockoutUntil = 0;

        public synchronized boolean isLockedOut() {
            long now = System.currentTimeMillis();
            if (lockoutUntil > now) {
                return true;
            }
            if (lockoutUntil != 0 && now >= lockoutUntil) {
                failedAttempts = 0;
                lockoutUntil = 0;
            }
            return false;
        }

        public synchronized void recordFailure() {
            long now = System.currentTimeMillis();
            if (lockoutUntil != 0 && now >= lockoutUntil) {
                failedAttempts = 0;
                lockoutUntil = 0;
            }
            failedAttempts++;
            if (failedAttempts >= MAX_LOGIN_ATTEMPTS) {
                lockoutUntil = now + LOCKOUT_DURATION_MS;
            }
        }

        public synchronized void recordSuccess() {
            failedAttempts = 0;
            lockoutUntil = 0;
        }
    }

    public AuthResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase();
        LoginAttemptTracker tracker = loginAttempts.computeIfAbsent(email, k -> new LoginAttemptTracker());
        if (tracker.isLockedOut()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Please try again after 15 minutes.");
        }

        User u = users.findByEmail(email)
                .filter(x -> encoder.matches(r.password(), x.getPassword()))
                .orElse(null);

        if (u == null) {
            tracker.recordFailure();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        tracker.recordSuccess();

        if (!u.isEnabled())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been disabled");
        return response(u);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User u = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!encoder.matches(currentPassword, u.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 6 characters");
        }
        u.setPassword(encoder.encode(newPassword));
        users.save(u);
    }

    private AuthResponse response(User u) {
        return new AuthResponse(jwt.generate(u), u.getRole().name(), u.getName(), u.getId());
    }
}
