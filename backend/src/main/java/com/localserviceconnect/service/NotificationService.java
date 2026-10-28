package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.NotificationDto;
import com.localserviceconnect.entity.Booking;
import com.localserviceconnect.entity.BookingStatus;
import com.localserviceconnect.entity.BookingType;
import com.localserviceconnect.entity.Notification;
import com.localserviceconnect.entity.Role;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.BookingRepository;
import com.localserviceconnect.repository.NotificationRepository;
import com.localserviceconnect.repository.ProviderRepository;
import com.localserviceconnect.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ProviderRepository providerRepository;

    @Value("${app.quick-service.unassigned-threshold-minutes:5}")
    private int unassignedThresholdMinutes;

    @Value("${app.quick-service.arrival-delay-threshold-minutes:20}")
    private int arrivalDelayThresholdMinutes;

    @Value("${app.quick-service.alert-cooldown-minutes:30}")
    private int alertCooldownMinutes;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    @Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            ProviderRepository providerRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.providerRepository = providerRepository;
    }

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository
    ) {
        this(notificationRepository, userRepository, null, null);
    }

    /**
     * Subscribe to real-time notifications via Server-Sent Events (SSE).
     */
    public SseEmitter subscribe(String email) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        subscribers.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = subscribers.get(email);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    subscribers.remove(email);
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial connection event with current unread count and latest pending notification
        try {
            long unread = notificationRepository.countByRecipientEmailAndReadFalse(email);
            Map<String, Object> initData = new HashMap<>();
            initData.put("type", "CONNECTED");
            initData.put("unreadCount", unread);
            initData.put("timestamp", LocalDateTime.now().toString());

            emitter.send(SseEmitter.event()
                    .name("init")
                    .data(initData));

            // Also send latest pending booking if provider has one waiting
            Optional<Notification> latestPending = notificationRepository
                    .findFirstByRecipientEmailAndTypeAndBookingStatusOrderByCreatedAtDesc(email, "NEW_BOOKING", "PENDING");
            if (latestPending.isPresent()) {
                emitter.send(SseEmitter.event()
                        .name("pending_booking")
                        .data(toDto(latestPending.get())));
            }
        } catch (IOException e) {
            log.debug("Failed to send initial SSE init event to {}: {}", email, e.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    /**
     * Broadcast keepalive ping to all active SSE subscribers to prevent proxy/browser timeout.
     */
    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        if (subscribers.isEmpty()) return;
        List<String> toClean = new ArrayList<>();

        subscribers.forEach((email, emitters) -> {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
            if (emitters.isEmpty()) {
                toClean.add(email);
            }
        });

        toClean.forEach(subscribers::remove);
    }

    /**
     * Notify all administrators of a platform event with deduplication support.
     */
    @Transactional
    public void notifyAdmins(String type, String title, String message, String entityType,
                             Long entityId, String targetUrl, String priority, String dedupKey) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        for (User admin : admins) {
            if (dedupKey != null && notificationRepository.existsByRecipientIdAndDedupKey(admin.getId(), dedupKey)) {
                log.debug("Notification dedupKey {} already exists for admin {}", dedupKey, admin.getEmail());
                continue;
            }

            Notification notif = new Notification();
            notif.setRecipient(admin);
            notif.setTitle(title);
            notif.setMessage(message);
            notif.setType(type);
            notif.setEntityType(entityType);
            notif.setEntityId(entityId);
            notif.setTargetUrl(targetUrl);
            notif.setPriority(priority != null ? priority : "NORMAL");
            notif.setDedupKey(dedupKey);
            notif.setRead(false);
            notif.setCreatedAt(LocalDateTime.now());

            Notification saved = notificationRepository.save(notif);
            NotificationDto dto = toDto(saved);

            emitToUser(admin.getEmail(), "admin_notification", dto);
            emitToUser(admin.getEmail(), "new_notification", dto);
            broadcastUnreadCount(admin.getEmail());
        }
    }

    /**
     * Persist and broadcast a new booking notification to the assigned provider.
     */
    @Transactional
    public NotificationDto sendBookingNotification(Booking booking) {
        if (booking == null || booking.getProvider() == null || booking.getProvider().getUser() == null) {
            return null;
        }

        User providerUser = booking.getProvider().getUser();
        Long providerUserId = providerUser.getId();

        // Prevent duplicate notification creation if event is retried
        if (notificationRepository.existsByRecipientIdAndBookingIdAndType(providerUserId, booking.getId(), "NEW_BOOKING")) {
            log.debug("Booking notification already exists for booking {}", booking.getId());
            return notificationRepository.findByBookingId(booking.getId()).stream()
                    .filter(n -> "NEW_BOOKING".equals(n.getType()))
                    .findFirst()
                    .map(this::toDto)
                    .orElse(null);
        }

        boolean isQuick = booking.getBookingType() == BookingType.QUICK_SERVICE;
        String title = isQuick ? "Urgent: Quick Service Request" : "New Service Booking Received";
        String customerName = booking.getCustomer() != null && booking.getCustomer().getUser() != null
                ? booking.getCustomer().getUser().getName() : "Customer";
        String serviceName = booking.getService() != null ? booking.getService().getName() : "Service";
        String location = booking.getFormattedAddress() != null && !booking.getFormattedAddress().isBlank()
                ? booking.getFormattedAddress()
                : (booking.getAddress() + (booking.getArea() != null ? ", " + booking.getArea() : ""));

        BigDecimal price = booking.getProvider().getApproxPrice();

        Notification notif = new Notification();
        notif.setRecipient(providerUser);
        notif.setTitle(title);
        notif.setMessage("New request from " + customerName + " for " + serviceName + " in " + booking.getArea());
        notif.setType("NEW_BOOKING");
        notif.setBooking(booking);
        notif.setBookingId(booking.getId());
        notif.setServiceName(serviceName);
        notif.setCustomerName(customerName);
        notif.setLocation(location);
        notif.setBookingType(booking.getBookingType().name());
        notif.setBookingDate(booking.getBookingDate());
        notif.setBookingTime(booking.getBookingTime());
        notif.setPrice(price);
        notif.setBookingStatus(booking.getStatus().name());
        notif.setRead(false);
        notif.setCreatedAt(LocalDateTime.now());
        notif.setTargetUrl("/provider/dashboard?tab=requests&bookingId=" + booking.getId());
        notif.setEntityType("BOOKING");
        notif.setEntityId(booking.getId());
        notif.setPriority(isQuick ? "HIGH" : "NORMAL");
        notif.setDedupKey("BOOKING_" + booking.getId());

        Notification saved = notificationRepository.save(notif);
        NotificationDto dto = toDto(saved);

        // Real-time delivery to provider
        emitToUser(providerUser.getEmail(), "new_booking", dto);
        broadcastUnreadCount(providerUser.getEmail());

        return dto;
    }

    /**
     * Update notification status when booking is accepted, rejected, or cancelled.
     */
    @Transactional
    public void sendBookingStatusUpdate(Booking booking, String previousStatus) {
        if (booking == null) return;
        String newStatus = booking.getStatus().name();
        List<Notification> notifs = notificationRepository.findByBookingId(booking.getId());
        for (Notification n : notifs) {
            n.setBookingStatus(newStatus);
            notificationRepository.save(n);
        }

        Map<String, Object> updateEvent = new HashMap<>();
        updateEvent.put("bookingId", booking.getId());
        updateEvent.put("status", newStatus);
        updateEvent.put("previousStatus", previousStatus);
        updateEvent.put("timestamp", LocalDateTime.now().toString());

        // Notify provider
        if (booking.getProvider() != null && booking.getProvider().getUser() != null) {
            String providerEmail = booking.getProvider().getUser().getEmail();
            emitToUser(providerEmail, "booking_updated", updateEvent);
            broadcastUnreadCount(providerEmail);
        }

        // Notify customer
        if (booking.getCustomer() != null && booking.getCustomer().getUser() != null) {
            String customerEmail = booking.getCustomer().getUser().getEmail();
            emitToUser(customerEmail, "booking_updated", updateEvent);
            broadcastUnreadCount(customerEmail);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications(String email) {
        return notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getMyNotificationsPaged(String email, String type, Boolean read,
                                                         String priority, String search,
                                                         int page, int size) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("recipient").get("email"), email));

            if (StringUtils.hasText(type) && !"ALL".equalsIgnoreCase(type)) {
                predicates.add(cb.equal(root.get("type"), type.trim()));
            }
            if (read != null) {
                predicates.add(cb.equal(root.get("read"), read));
            }
            if (StringUtils.hasText(priority) && !"ALL".equalsIgnoreCase(priority)) {
                predicates.add(cb.equal(root.get("priority"), priority.trim().toUpperCase()));
            }
            if (StringUtils.hasText(search)) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate searchPred = cb.or(
                        cb.like(cb.lower(root.get("title")), term),
                        cb.like(cb.lower(root.get("message")), term),
                        cb.like(cb.lower(root.get("type")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("serviceName"), "")), term),
                        cb.like(cb.lower(cb.coalesce(root.get("customerName"), "")), term)
                );
                predicates.add(searchPred);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        return notificationRepository.countByRecipientEmailAndReadFalse(email);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationDto> getLatestPending(String email) {
        return notificationRepository
                .findFirstByRecipientEmailAndTypeAndBookingStatusOrderByCreatedAtDesc(email, "NEW_BOOKING", "PENDING")
                .map(this::toDto);
    }

    @Transactional
    public NotificationDto markAsRead(Long id, String email) {
        Notification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notif.getRecipient().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to notification");
        }
        notif.setRead(true);
        Notification saved = notificationRepository.save(notif);
        broadcastUnreadCount(email);
        return toDto(saved);
    }

    @Transactional
    public NotificationDto toggleRead(Long id, String email, boolean read) {
        Notification notif = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notif.getRecipient().getEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to notification");
        }
        notif.setRead(read);
        Notification saved = notificationRepository.save(notif);
        broadcastUnreadCount(email);
        return toDto(saved);
    }

    @Transactional
    public void markAllAsRead(String email) {
        List<Notification> list = notificationRepository.findByRecipientEmailOrderByCreatedAtDesc(email);
        for (Notification n : list) {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }
        broadcastUnreadCount(email);
    }

    @Transactional
    public NotificationDto sendVerificationNotification(User recipient, String type, String title, String message) {
        if (recipient == null) return null;
        User target = recipient;
        if (target.getId() != null) {
            target = userRepository.findById(target.getId()).orElse(recipient);
        }
        Notification n = new Notification();
        n.setRecipient(target);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        n.setTargetUrl("/provider/home");
        Notification saved = notificationRepository.save(n);

        String email = target.getEmail();
        Map<String, Object> verifEvent = new HashMap<>();
        verifEvent.put("id", saved.getId());
        verifEvent.put("title", title);
        verifEvent.put("message", message);
        verifEvent.put("type", type);
        verifEvent.put("timestamp", LocalDateTime.now().toString());

        if (email != null) {
            emitToUser(email, "verification_updated", verifEvent);
            broadcastUnreadCount(email);
        }
        return toDto(saved);
    }

    /**
     * Periodic scanner for Quick Service health alerts with configurable thresholds.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkQuickServiceAlerts() {
        if (bookingRepository == null || providerRepository == null) return;
        try {
            List<Booking> activeQuickBookings = bookingRepository.findByBookingTypeAndStatusInOrderByIdDesc(
                    BookingType.QUICK_SERVICE,
                    List.of(BookingStatus.PENDING, BookingStatus.ACCEPTED, BookingStatus.ON_THE_WAY)
            );

            if (activeQuickBookings.isEmpty()) return;

            LocalDateTime now = LocalDateTime.now();
            long cooldownWindow = Math.max(1, alertCooldownMinutes) * 60_000L;
            long windowId = System.currentTimeMillis() / cooldownWindow;

            for (Booking b : activeQuickBookings) {
                LocalDateTime created = b.getCreatedAt() != null ? b.getCreatedAt() : now;
                long minutes = Math.max(0, Duration.between(created, now).toMinutes());
                String serviceName = b.getService() != null ? b.getService().getName() : "Quick Service";
                String area = b.getArea() != null ? b.getArea() : "Service Area";

                if (b.getStatus() == BookingStatus.PENDING) {
                    // Check if eligible providers exist
                    String areaClean = b.getArea() != null ? b.getArea().trim().toLowerCase() : "";
                    boolean hasProviders = b.getService() != null && !providerRepository.match(b.getService().getId(), areaClean, true).isEmpty();

                    if (!hasProviders) {
                        String dedupKey = "QS_NO_PROV_" + b.getId() + "_" + windowId;
                        notifyAdmins(
                                "QUICK_SERVICE_NO_PROVIDER",
                                "No Provider Available: Quick Service #BK-" + b.getId(),
                                "No eligible Quick Service provider currently available in " + area + " for " + serviceName + ". Manual intervention required.",
                                "BOOKING",
                                b.getId(),
                                "/admin/bookings.html?type=QUICK_SERVICE&bookingId=" + b.getId(),
                                "URGENT",
                                dedupKey
                        );
                    } else if (minutes >= unassignedThresholdMinutes) {
                        String dedupKey = "QS_UNASSIGNED_" + b.getId() + "_" + windowId;
                        notifyAdmins(
                                "QUICK_SERVICE_UNASSIGNED",
                                "Unassigned Quick Service: #BK-" + b.getId(),
                                "Quick Service request #BK-" + b.getId() + " (" + serviceName + ") has been unassigned for " + minutes + " minutes (threshold is " + unassignedThresholdMinutes + " mins).",
                                "BOOKING",
                                b.getId(),
                                "/admin/bookings.html?type=QUICK_SERVICE&bookingId=" + b.getId(),
                                "HIGH",
                                dedupKey
                        );
                    }
                } else if (b.getStatus() == BookingStatus.ON_THE_WAY) {
                    if (minutes >= arrivalDelayThresholdMinutes) {
                        String dedupKey = "QS_DELAY_" + b.getId() + "_" + windowId;
                        notifyAdmins(
                                "QUICK_SERVICE_ALERT",
                                "Arrival Delay Alert: Quick Service #BK-" + b.getId(),
                                "Provider for Quick Service #BK-" + b.getId() + " has been 'On The Way' for " + minutes + " minutes (target arrival threshold of " + arrivalDelayThresholdMinutes + " mins exceeded).",
                                "BOOKING",
                                b.getId(),
                                "/admin/bookings.html?type=QUICK_SERVICE&bookingId=" + b.getId(),
                                "HIGH",
                                dedupKey
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error evaluating quick service alert conditions: {}", e.getMessage(), e);
        }
    }

    private void broadcastUnreadCount(String email) {
        long count = notificationRepository.countByRecipientEmailAndReadFalse(email);
        Map<String, Object> countData = new HashMap<>();
        countData.put("unreadCount", count);
        emitToUser(email, "unread_count", countData);
    }

    private void emitToUser(String email, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(email);
        if (emitters == null || emitters.isEmpty()) return;

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    public NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getTitle(),
                n.getMessage(),
                n.getType(),
                n.getBookingId(),
                n.getServiceName(),
                n.getCustomerName(),
                n.getLocation(),
                n.getBookingType(),
                n.getBookingDate(),
                n.getBookingTime(),
                n.getPrice(),
                n.getBookingStatus(),
                n.isRead(),
                n.getCreatedAt(),
                n.getTargetUrl(),
                n.getEntityType(),
                n.getEntityId(),
                n.getPriority() != null ? n.getPriority() : "NORMAL"
        );
    }
}
