package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.NotificationDto;
import com.localserviceconnect.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Real-time Server-Sent Events (SSE) notification stream.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return notificationService.subscribe(authentication.getName());
    }

    /**
     * Get all notifications for the authenticated user (supports pagination and filtering).
     */
    @GetMapping
    public ResponseEntity<?> list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search
    ) {
        if (page != null) {
            int pageIndex = Math.max(0, page);
            int pageSize = (size != null && size > 0 && size <= 100) ? size : 15;
            return ResponseEntity.ok(notificationService.getMyNotificationsPaged(
                    authentication.getName(), type, read, priority, search, pageIndex, pageSize));
        }
        return ResponseEntity.ok(notificationService.getMyNotifications(authentication.getName()));
    }

    /**
     * Get unread notification count.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(authentication.getName())));
    }

    /**
     * Get the latest pending booking notification for display in provider banner.
     */
    @GetMapping("/latest-pending")
    public ResponseEntity<NotificationDto> latestPending(Authentication authentication) {
        return notificationService.getLatestPending(authentication.getName())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Mark a single notification as read.
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markRead(Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication.getName()));
    }

    /**
     * Toggle read/unread status of a notification.
     */
    @PutMapping("/{id}/toggle-read")
    public ResponseEntity<NotificationDto> toggleRead(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean read) {
        return ResponseEntity.ok(notificationService.toggleRead(id, authentication.getName(), read));
    }

    /**
     * Mark all notifications as read.
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, String>> markAllRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
