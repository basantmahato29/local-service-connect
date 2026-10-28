package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.Role;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.UserRepository;
import com.localserviceconnect.service.AdminService;
import com.localserviceconnect.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final com.localserviceconnect.service.NotificationService notificationService;
    private final com.localserviceconnect.service.AdminActivityLogService adminActivityLogService;

    @Autowired
    public AdminController(AdminService adminService, ReviewService reviewService, UserRepository userRepository,
                           com.localserviceconnect.service.NotificationService notificationService,
                           com.localserviceconnect.service.AdminActivityLogService adminActivityLogService) {
        this.adminService = adminService;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.adminActivityLogService = adminActivityLogService;
    }

    public AdminController(AdminService adminService, ReviewService reviewService, UserRepository userRepository) {
        this(adminService, reviewService, userRepository, null, null);
    }

    private User requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByEmail(auth.getName())
                .filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges required"));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> dashboard() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/analytics")
    public ResponseEntity<AdminAnalyticsDto> getAnalytics(
            @RequestParam(defaultValue = "30d") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(adminService.getFullAnalytics(period, fromDate, toDate));
    }

    @GetMapping("/providers")
    public ResponseEntity<?> getProviders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String subService,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        if (page != null) {
            int pageIndex = Math.max(0, page);
            int pageSize = (size != null && size > 0 && size <= 100) ? size : 15;
            return ResponseEntity.ok(adminService.providersPaged(
                    status, search, serviceId, subService, area, minRating, available, enabled,
                    pageIndex, pageSize, sortBy, sortDir
            ));
        }
        return ResponseEntity.ok(adminService.providers(
                status, search, serviceId, subService, area, minRating, available, enabled,
                sortBy, sortDir
        ));
    }

    @GetMapping("/providers/{id}/details")
    public ResponseEntity<AdminProviderDetailDto> getProviderDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getProviderDetails(id));
    }

    @GetMapping("/providers/{id}/history")
    public ResponseEntity<List<ProviderActionLogDto>> getProviderHistory(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getProviderActionHistory(id));
    }

    @GetMapping("/quick-service/monitor")
    public ResponseEntity<QuickServiceMonitorSummaryDto> monitorQuickService() {
        return ResponseEntity.ok(adminService.getQuickServiceMonitoring());
    }

    @PutMapping("/providers/{id}/verify")
    public ResponseEntity<AdminProviderDto> verifyProvider(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestParam(required = false) String status) {
        String finalStatus = (body != null && body.containsKey("status")) ? body.get("status") : status;
        if (finalStatus == null || finalStatus.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        return ResponseEntity.ok(adminService.verify(id, finalStatus));
    }

    @PutMapping("/providers/{id}/enabled")
    public ResponseEntity<AdminProviderDto> setProviderEnabled(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Boolean enabled) {
        User adminUser = requireAdmin(auth);
        boolean finalEnabled = (body != null && body.containsKey("enabled"))
                ? Boolean.parseBoolean(String.valueOf(body.get("enabled")))
                : (enabled != null && enabled);
        String reason = (body != null && body.containsKey("reason"))
                ? String.valueOf(body.get("reason")) : null;
        return ResponseEntity.ok(adminService.setProviderEnabled(id, finalEnabled, reason, adminUser));
    }

    @PutMapping("/providers/{id}")
    public ResponseEntity<AdminProviderDto> editProvider(
            @PathVariable Long id,
            @RequestBody ProviderUpdateRequest request) {
        return ResponseEntity.ok(adminService.editProvider(id, request));
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<Map<String, Object>> deleteProvider(@PathVariable Long id) {
        adminService.deleteProvider(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Provider permanently deleted"));
    }

    @DeleteMapping("/verification/applications/{id}")
    public ResponseEntity<Map<String, Object>> deleteVerificationApplication(@PathVariable Long id) {
        adminService.deleteVerificationApplication(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Verification application permanently deleted"));
    }

    @GetMapping("/customers")
    public ResponseEntity<List<AdminCustomerDto>> getCustomers() {
        return ResponseEntity.ok(adminService.customers());
    }

    @PutMapping("/customers/{id}/enabled")
    public ResponseEntity<AdminCustomerDto> setCustomerEnabled(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(required = false) Boolean enabled) {
        boolean finalEnabled = (body != null && body.containsKey("enabled"))
                ? Boolean.parseBoolean(String.valueOf(body.get("enabled")))
                : (enabled != null && enabled);
        return ResponseEntity.ok(adminService.setCustomerEnabled(id, finalEnabled));
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> getBookings(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String subService,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        if (page != null) {
            int pageNum = page != null ? page : 0;
            int pageSize = size != null ? size : 15;
            return ResponseEntity.ok(adminService.bookingsPaged(
                    search, status, type, serviceId, subService, providerId, customerId, area, fromDate, toDate,
                    pageNum, pageSize, sortBy, sortDir
            ));
        }
        return ResponseEntity.ok(adminService.bookings(
                search, status, type, serviceId, subService, providerId, customerId, area, fromDate, toDate,
                sortBy, sortDir
        ));
    }

    @GetMapping("/bookings/{id}/details")
    public ResponseEntity<AdminBookingDetailDto> getBookingDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getBookingDetails(id));
    }

    @GetMapping("/bookings/{id}/history")
    public ResponseEntity<List<BookingStatusHistoryDto>> getBookingHistory(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getBookingHistory(id));
    }

    @PutMapping("/bookings/{id}/status")
    public ResponseEntity<AdminBookingDetailDto> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody AdminBookingActionRequest req,
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user) {
        return ResponseEntity.ok(adminService.adminUpdateBooking(id, req, user));
    }

    @GetMapping("/bookings/analytics")
    public ResponseEntity<BookingAnalyticsDto> getBookingAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(adminService.getBookingAnalytics(fromDate, toDate));
    }

    @GetMapping("/reviews")
    public ResponseEntity<List<AdminReviewDto>> getReviews() {
        return ResponseEntity.ok(reviewService.adminList());
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id, Authentication authentication) {
        reviewService.delete(id);
        if (adminActivityLogService != null) {
            User admin = authentication != null ? userRepository.findByEmail(authentication.getName()).orElse(null) : null;
            adminActivityLogService.record(
                    admin,
                    "REVIEW_DELETED",
                    "REVIEW",
                    id,
                    "Review #" + id,
                    "Administrator moderated and deleted review #" + id,
                    null,
                    "/admin/reviews.html"
            );
        }
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ADMIN NOTIFICATION CENTER ENDPOINTS
    // =========================================================================

    @GetMapping("/notifications")
    public ResponseEntity<?> getAdminNotifications(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search
    ) {
        requireAdmin(authentication);
        if (notificationService == null) {
            return ResponseEntity.ok(List.of());
        }
        if (page != null) {
            int pageIndex = Math.max(0, page);
            int pageSize = (size != null && size > 0 && size <= 100) ? size : 15;
            return ResponseEntity.ok(notificationService.getMyNotificationsPaged(
                    authentication.getName(), type, read, priority, search, pageIndex, pageSize));
        }
        return ResponseEntity.ok(notificationService.getMyNotifications(authentication.getName()));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Long>> getAdminUnreadCount(Authentication authentication) {
        requireAdmin(authentication);
        long count = notificationService != null ? notificationService.getUnreadCount(authentication.getName()) : 0L;
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<NotificationDto> markAdminNotificationRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireAdmin(authentication);
        if (notificationService == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification service not available");
        }
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication.getName()));
    }

    @PutMapping("/notifications/{id}/toggle-read")
    public ResponseEntity<NotificationDto> toggleAdminNotificationRead(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean read
    ) {
        requireAdmin(authentication);
        if (notificationService == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification service not available");
        }
        return ResponseEntity.ok(notificationService.toggleRead(id, authentication.getName(), read));
    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<Map<String, String>> markAllAdminNotificationsRead(Authentication authentication) {
        requireAdmin(authentication);
        if (notificationService != null) {
            notificationService.markAllAsRead(authentication.getName());
        }
        return ResponseEntity.ok(Map.of("message", "All administrator notifications marked as read"));
    }

    // =========================================================================
    // ADMIN ACTIVITY LOG AUDIT TRAIL ENDPOINTS
    // =========================================================================

    @GetMapping("/activity-logs")
    public ResponseEntity<?> getActivityLogs(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "timestamp") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        requireAdmin(authentication);
        if (adminActivityLogService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(adminActivityLogService.getLogsPaged(
                search, actionType, entityType, actorEmail, fromDate, toDate, page, size, sortBy, sortDir
        ));
    }

    @GetMapping("/activity-logs/action-types")
    public ResponseEntity<List<String>> getActivityActionTypes(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(adminActivityLogService != null ? adminActivityLogService.getActionTypes() : List.of());
    }

    @GetMapping("/activity-logs/entity-types")
    public ResponseEntity<List<String>> getActivityEntityTypes(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(adminActivityLogService != null ? adminActivityLogService.getEntityTypes() : List.of());
    }

    @GetMapping("/activity-logs/{id}")
    public ResponseEntity<AdminActivityLogDto> getActivityLogById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        requireAdmin(authentication);
        if (adminActivityLogService == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Log service not available");
        }
        return adminActivityLogService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity log not found"));
    }
}
