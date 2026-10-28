package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.util.GeoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final CustomerRepository customers;
    private final ProviderRepository providers;
    private final ServiceTypeRepository serviceTypes;
    private final ReviewRepository reviews;
    private final OtpService otpService;
    private final ProviderProfileService providerProfileService;
    private final NotificationService notificationService;
    private final BookingStatusHistoryService historyService;

    public BookingService(BookingRepository bookings, CustomerRepository customers,
                          ProviderRepository providers, ServiceTypeRepository serviceTypes,
                          ReviewRepository reviews, OtpService otpService,
                          ProviderProfileService providerProfileService,
                          NotificationService notificationService,
                          BookingStatusHistoryService historyService) {
        this.bookings = bookings;
        this.customers = customers;
        this.providers = providers;
        this.serviceTypes = serviceTypes;
        this.reviews = reviews;
        this.otpService = otpService;
        this.providerProfileService = providerProfileService;
        this.notificationService = notificationService;
        this.historyService = historyService;
    }

    @Transactional
    public BookingDto create(String email, BookingRequest r) {
        Customer c = customers.findByUserEmail(email).orElseThrow(() -> bad("Customer profile not found"));
        if (r.serviceId() == null) throw bad("Service ID is required");
        if (r.providerId() == null) throw bad("Provider ID is required");
        if (r.bookingType() == null) throw bad("Booking type is required");
        String address = first(r.address(), c.getAddress()), area = first(r.area(), c.getArea());
        if (address == null || area == null) throw bad("Please provide your address and area");
        ServiceType svc = serviceTypes.findById(r.serviceId())
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE).orElseThrow(() -> bad("This service is not available"));

        boolean quick = r.bookingType() == BookingType.QUICK_SERVICE;
        LocalDate date; LocalTime time;
        if (quick) { // Quick Service is "now"
            LocalDateTime now = LocalDateTime.now();
            date = now.toLocalDate(); time = now.toLocalTime().withNano(0);
        } else {
            if (r.bookingDate() == null || r.bookingTime() == null) throw bad("Please select a date and time");
            LocalDateTime when = LocalDateTime.of(r.bookingDate(), r.bookingTime());
            if (!when.isAfter(LocalDateTime.now().plusMinutes(30))) throw bad("Please choose a time at least 30 minutes from now");
            if (when.isAfter(LocalDateTime.now().plusDays(90))) throw bad("Bookings can be made up to 90 days ahead");
            date = r.bookingDate(); time = r.bookingTime();
        }

        Double reqLat = r.latitude() != null ? r.latitude() : c.getLatitude();
        Double reqLon = r.longitude() != null ? r.longitude() : c.getLongitude();
        if ((reqLat != null || reqLon != null) && !GeoUtils.isValidCoordinates(reqLat, reqLon)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid booking coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
        }

        // Re-run the matching rules (supporting coordinates or area) so a stale page can't book an unavailable provider
        boolean ok;
        if (reqLat != null && reqLon != null) {
            ok = providerProfileService.match(svc.getId(), area != null ? area.trim().toLowerCase() : "", reqLat, reqLon, quick).stream()
                    .anyMatch(p -> p.id().equals(r.providerId()));
        } else {
            ok = providerProfileService.match(svc.getId(), area != null ? area.trim().toLowerCase() : "", null, null, quick).stream()
                    .anyMatch(p -> p.id().equals(r.providerId()));
        }

        if (!ok) throw new ResponseStatusException(HttpStatus.CONFLICT, quick
                ? "This provider is no longer available for Quick Service. Please pick another."
                : "This provider is not available for this service in your area");
        if (bookings.existsByCustomerIdAndProviderIdAndStatus(c.getId(), r.providerId(), BookingStatus.PENDING))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a pending request with this provider");

        Booking b = new Booking();
        b.setCustomer(c); b.setProvider(providers.getReferenceById(r.providerId())); b.setService(svc);
        b.setSubServiceId(r.subServiceId());
        b.setSubServiceName(first(r.subServiceName(), null));
        b.setAddress(address.trim()); b.setArea(area.trim()); b.setBookingType(r.bookingType());
        b.setBookingDate(date); b.setBookingTime(time); b.setNotes(first(r.notes(), null));
        b.setFormattedAddress(first(r.formattedAddress(), c.getFormattedAddress()));
        b.setLatitude(reqLat);
        b.setLongitude(reqLon);
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), null, BookingStatus.PENDING.name(), "CREATE",
                "Booking requested by customer", c.getUser().getId(), c.getUser().getName(), "CUSTOMER");
        notificationService.sendBookingNotification(saved);

        boolean isQuick = saved.getBookingType() == BookingType.QUICK_SERVICE;
        notificationService.notifyAdmins(
                isQuick ? "QUICK_SERVICE_DISPATCH" : "NEW_BOOKING_REQUEST",
                isQuick ? "Quick Service Dispatch #BK-" + saved.getId() : "New Booking Request #BK-" + saved.getId(),
                (isQuick ? "Urgent Quick Service request" : "New booking request") + " from " + c.getUser().getName() + " for " + svc.getName() + " in " + saved.getArea(),
                "BOOKING",
                saved.getId(),
                "/admin/bookings.html?bookingId=" + saved.getId() + (isQuick ? "&type=QUICK_SERVICE" : ""),
                isQuick ? "HIGH" : "NORMAL",
                "BOOKING_CREATE_" + saved.getId()
        );

        return toDto(saved, "CUSTOMER");
    }

    @Transactional(readOnly = true)
    public List<BookingDto> my(String email, String role) {
        var list = "PROVIDER".equals(role) ? bookings.findByProviderUserEmailOrderByCreatedAtDesc(email)
                                           : bookings.findByCustomerUserEmailOrderByCreatedAtDesc(email);
        return list.stream().map(b -> toDto(b, role)).toList();
    }

    @Transactional(readOnly = true)
    public BookingDto get(Long id, String email, String role) {
        Booking b = find(id);
        boolean party = b.getCustomer().getUser().getEmail().equals(email) || b.getProvider().getUser().getEmail().equals(email);
        if (!party && !"ADMIN".equals(role)) throw notFound(); // 404 so we don't reveal other people's booking ids
        return toDto(b, role);
    }

    @Transactional
    public BookingDto cancel(Long id, String email) {
        Booking b = bookings.findWithLockById(id).orElseThrow(BookingService::notFound);
        if (!b.getCustomer().getUser().getEmail().equals(email)) throw notFound();
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.ACCEPTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking can no longer be cancelled");
        BookingStatus prev = b.getStatus();
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancellationReason("Cancelled by customer");
        otpService.invalidateForBooking(b.getId());
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), prev.name(), BookingStatus.CANCELLED.name(), "CANCEL",
                "Customer cancelled booking", b.getCustomer().getUser().getId(), b.getCustomer().getUser().getName(), "CUSTOMER");
        notificationService.sendBookingStatusUpdate(saved, prev.name());

        notificationService.notifyAdmins(
                "BOOKING_CANCELLED",
                "Booking Cancelled #BK-" + saved.getId(),
                "Customer " + b.getCustomer().getUser().getName() + " cancelled booking #BK-" + saved.getId() + " (" + (b.getService() != null ? b.getService().getName() : "Service") + ").",
                "BOOKING",
                saved.getId(),
                "/admin/bookings.html?bookingId=" + saved.getId(),
                "NORMAL",
                "BOOKING_CANCEL_" + saved.getId()
        );

        return toDto(saved, "CUSTOMER");
    }

    @Transactional
    public BookingDto accept(Long id, String email) {
        Booking b = owned(id, email);
        require(b, BookingStatus.PENDING, "Only pending requests can be accepted");
        if (b.getBookingType() == BookingType.QUICK_SERVICE && busy(b.getProvider().getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finish your current Quick Service job before accepting another");
        b.setStatus(BookingStatus.ACCEPTED);
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), BookingStatus.PENDING.name(), BookingStatus.ACCEPTED.name(), "ACCEPT",
                "Provider accepted booking request", b.getProvider().getUser().getId(), b.getProvider().getUser().getName(), "PROVIDER");
        // Automatically generate 4-digit service-start OTP for the customer
        otpService.createOrUpdateOtp(saved);
        notificationService.sendBookingStatusUpdate(saved, "PENDING");
        return toDto(saved, "PROVIDER");
    }

    @Transactional
    public BookingDto reject(Long id, String email) {
        Booking b = owned(id, email);
        require(b, BookingStatus.PENDING, "Only pending requests can be rejected");
        b.setStatus(BookingStatus.REJECTED);
        b.setCancellationReason("Declined by provider");
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), BookingStatus.PENDING.name(), BookingStatus.REJECTED.name(), "REJECT",
                "Provider declined booking request", b.getProvider().getUser().getId(), b.getProvider().getUser().getName(), "PROVIDER");
        notificationService.sendBookingStatusUpdate(saved, "PENDING");
        return toDto(saved, "PROVIDER");
    }

    /** 
     * Status transitions:
     * - ACCEPTED -> ON_THE_WAY (allowed)
     * - ON_THE_WAY / ACCEPTED -> SERVICE_STARTED (MUST be done via OTP verification, cannot be bypassed manually)
     * - SERVICE_STARTED -> AWAITING_COMPLETION (provider marks service finished, awaiting customer confirmation)
     * - AWAITING_COMPLETION -> COMPLETED (MUST be confirmed by customer)
     */
    @Transactional
    public BookingDto advance(Long id, String email, String target) {
        Booking b = owned(id, email);
        BookingStatus next;
        try { next = BookingStatus.valueOf(target.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw bad("Unknown status: " + target); }

        if (next == BookingStatus.SERVICE_STARTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Service cannot be started without customer OTP verification. Please verify the customer OTP.");
        }
        if (next == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Service completion requires customer confirmation. You can mark the service finished to request customer confirmation.");
        }

        BookingStatus expected = switch (b.getStatus()) {
            case ACCEPTED -> BookingStatus.ON_THE_WAY;
            case SERVICE_STARTED -> BookingStatus.AWAITING_COMPLETION;
            default -> null;
        };
        if (expected == null || next != expected)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That status change is not allowed from " + b.getStatus());
        BookingStatus prev = b.getStatus();
        b.setStatus(next);
        Booking saved = bookings.save(b);
        historyService.record(saved.getId(), prev.name(), next.name(), next.name(),
                "Provider updated status to " + next.name(), b.getProvider().getUser().getId(), b.getProvider().getUser().getName(), "PROVIDER");
        notificationService.sendBookingStatusUpdate(saved, prev.name());
        return toDto(saved, "PROVIDER");
    }

    private boolean busy(Long providerId) {
        return bookings.existsByProviderIdAndStatusIn(providerId, List.of(BookingStatus.ON_THE_WAY, BookingStatus.SERVICE_STARTED, BookingStatus.AWAITING_COMPLETION))
            || bookings.existsByProviderIdAndStatusAndBookingType(providerId, BookingStatus.ACCEPTED, BookingType.QUICK_SERVICE);
    }

    private Booking owned(Long id, String email) {
        Booking b = bookings.findWithLockById(id).orElseThrow(BookingService::notFound);
        if (!b.getProvider().getUser().getEmail().equals(email)) throw notFound();
        return b;
    }

    private static void require(Booking b, BookingStatus s, String message) {
        if (b.getStatus() != s) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** viewer = CUSTOMER, PROVIDER or ADMIN. Contact details and the full address are only shared once a booking is accepted. */
    public BookingDto toDto(Booking b, String viewer) {
        boolean shared = switch (b.getStatus()) {
            case ACCEPTED, ON_THE_WAY, SERVICE_STARTED, AWAITING_COMPLETION, COMPLETED -> true;
            default -> false;
        };
        boolean provider = "PROVIDER".equals(viewer), customer = "CUSTOMER".equals(viewer), admin = "ADMIN".equals(viewer);
        String address = (provider && !shared && !admin) ? b.getArea() : b.getAddress();
        String customerPhone = (shared || admin) && !customer ? (b.getCustomer() != null && b.getCustomer().getUser() != null ? b.getCustomer().getUser().getPhone() : null) : (admin && b.getCustomer() != null && b.getCustomer().getUser() != null ? b.getCustomer().getUser().getPhone() : null);
        String providerPhone = (shared || admin) && !provider ? (b.getProvider() != null && b.getProvider().getUser() != null ? b.getProvider().getUser().getPhone() : null) : (admin && b.getProvider() != null && b.getProvider().getUser() != null ? b.getProvider().getUser().getPhone() : null);
        boolean reviewed = reviews.existsByBookingId(b.getId());

        String formattedAddress = (provider && !shared && !admin) ? null : b.getFormattedAddress();
        Double customerLat = (provider && !shared && !admin) ? null : b.getLatitude();
        Double customerLon = (provider && !shared && !admin) ? null : b.getLongitude();

        Double providerLat = b.getProvider() != null ? b.getProvider().getLatitude() : null;
        Double providerLon = b.getProvider() != null ? b.getProvider().getLongitude() : null;
        Double distanceKm = null;
        if (b.getLatitude() != null && b.getLongitude() != null && providerLat != null && providerLon != null) {
            distanceKm = GeoUtils.distanceKm(b.getLatitude(), b.getLongitude(), providerLat, providerLon);
        }

        java.math.BigDecimal price = b.getProvider() != null ? b.getProvider().getApproxPrice() : null;

        String paymentStatus;
        if (b.getStatus() == BookingStatus.COMPLETED) {
            paymentStatus = "Paid on Completion (Cash/Direct)";
        } else if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) {
            paymentStatus = "Cancelled / Not Applicable";
        } else {
            paymentStatus = "Pay on Completion (Pending)";
        }

        String customerEmail = (customer || admin) && b.getCustomer() != null && b.getCustomer().getUser() != null
                ? b.getCustomer().getUser().getEmail() : null;
        String providerEmail = (provider || admin) && b.getProvider() != null && b.getProvider().getUser() != null
                ? b.getProvider().getUser().getEmail() : null;

        return new BookingDto(b.getId(), b.getService().getName(), b.getSubServiceId(), b.getSubServiceName(),
                b.getProvider().getId(),
                b.getProvider().getBusinessName(), b.getProvider().getBusinessName(),
                b.getCustomer().getUser().getName(), address, b.getArea(), b.getBookingType().name(),
                b.getBookingDate(), b.getBookingTime(), b.getStatus().name(), b.getNotes(), b.getCreatedAt(), b.getCompletedAt(),
                customerPhone, providerPhone, reviewed,
                formattedAddress, customerLat, customerLon, providerLat, providerLon, distanceKm,
                b.getCancellationReason(), price, paymentStatus, customerEmail, providerEmail);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<BookingDto> getEligibleReviewBooking(String customerEmail, Long providerId) {
        Customer c = customers.findByUserEmail(customerEmail).orElse(null);
        if (c == null) return java.util.Optional.empty();
        List<Booking> completed = bookings.findByCustomerIdAndProviderIdAndStatus(c.getId(), providerId, BookingStatus.COMPLETED);
        for (Booking b : completed) {
            if (!reviews.existsByBookingId(b.getId())) {
                return java.util.Optional.of(toDto(b, "CUSTOMER"));
            }
        }
        return java.util.Optional.empty();
    }

    private Booking find(Long id) { return bookings.findById(id).orElseThrow(BookingService::notFound); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"); }
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private static String first(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b != null && !b.isBlank() ? b.trim() : null;
    }
}
