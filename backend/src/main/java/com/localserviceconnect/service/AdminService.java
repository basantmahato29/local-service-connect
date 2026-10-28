package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import jakarta.persistence.criteria.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AdminService {
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.ON_THE_WAY,
            BookingStatus.SERVICE_STARTED,
            BookingStatus.AWAITING_COMPLETION
    );

    private final UserRepository users;
    private final ProviderRepository providers;
    private final ServiceTypeRepository services;
    private final BookingRepository bookings;
    private final ProviderProfileService profiles;
    private final CustomerRepository customers;
    private final BookingService bookingService;
    private final ProviderActionLogRepository actionLogs;
    private final VerificationApplicationRepository verificationAppRepo;
    private final VerificationService verificationService;
    private final ReviewRepository reviews;
    private final BookingStatusHistoryService historyService;
    private final ProviderAreaRepository providerAreas;
    private final BookingStatusHistoryRepository historyRepo;
    private final FavoriteRepository favoriteRepo;
    private final NotificationRepository notificationRepo;
    private final AdminActivityLogService adminActivityLogService;
    private final NotificationService notificationService;

    @Autowired
    public AdminService(UserRepository users, ProviderRepository providers, ServiceTypeRepository services,
                        BookingRepository bookings, ProviderProfileService profiles,
                        CustomerRepository customers, BookingService bookingService,
                        ProviderActionLogRepository actionLogs,
                        VerificationApplicationRepository verificationAppRepo,
                        VerificationService verificationService,
                        ReviewRepository reviews,
                        BookingStatusHistoryService historyService,
                        ProviderAreaRepository providerAreas,
                        BookingStatusHistoryRepository historyRepo,
                        FavoriteRepository favoriteRepo,
                        NotificationRepository notificationRepo,
                        AdminActivityLogService adminActivityLogService,
                        NotificationService notificationService) {
        this.users = users;
        this.providers = providers;
        this.services = services;
        this.bookings = bookings;
        this.profiles = profiles;
        this.customers = customers;
        this.bookingService = bookingService;
        this.actionLogs = actionLogs;
        this.verificationAppRepo = verificationAppRepo;
        this.verificationService = verificationService;
        this.reviews = reviews;
        this.historyService = historyService;
        this.providerAreas = providerAreas;
        this.historyRepo = historyRepo;
        this.favoriteRepo = favoriteRepo;
        this.notificationRepo = notificationRepo;
        this.adminActivityLogService = adminActivityLogService;
        this.notificationService = notificationService;
    }

    public AdminService(UserRepository users, ProviderRepository providers, ServiceTypeRepository services,
                        BookingRepository bookings, ProviderProfileService profiles,
                        CustomerRepository customers, BookingService bookingService,
                        ProviderActionLogRepository actionLogs,
                        VerificationApplicationRepository verificationAppRepo,
                        VerificationService verificationService,
                        ReviewRepository reviews,
                        BookingStatusHistoryService historyService,
                        ProviderAreaRepository providerAreas,
                        BookingStatusHistoryRepository historyRepo,
                        FavoriteRepository favoriteRepo,
                        NotificationRepository notificationRepo) {
        this(users, providers, services, bookings, profiles, customers, bookingService, actionLogs,
             verificationAppRepo, verificationService, reviews, historyService, providerAreas,
             historyRepo, favoriteRepo, notificationRepo, null, null);
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) byStatus.put(s.name(), bookings.countByStatus(s));
        long active = byStatus.getOrDefault("PENDING", 0L)
                + byStatus.getOrDefault("ACCEPTED", 0L)
                + byStatus.getOrDefault("ON_THE_WAY", 0L)
                + byStatus.getOrDefault("SERVICE_STARTED", 0L)
                + byStatus.getOrDefault("AWAITING_COMPLETION", 0L);
        return new DashboardDto(
                users.countByRole(Role.CUSTOMER),
                users.countByRole(Role.PROVIDER),
                providers.countByVerificationStatus(VerificationStatus.VERIFIED),
                providers.countByVerificationStatus(VerificationStatus.PENDING),
                services.count(),
                bookings.count(),
                active,
                byStatus.getOrDefault("COMPLETED", 0L),
                byStatus.getOrDefault("CANCELLED", 0L) + byStatus.getOrDefault("REJECTED", 0L),
                bookings.countByBookingType(BookingType.QUICK_SERVICE),
                byStatus
        );
    }

    @Transactional
    public AdminProviderDto verify(Long id, String status) {
        VerificationStatus st = parse(status);
        Provider p = providers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        p.setVerificationStatus(st);
        if (st != VerificationStatus.VERIFIED) p.setAvailability(false);
        return toDto(providers.save(p));
    }

    @Transactional(readOnly = true)
    public List<AdminCustomerDto> customers() {
        return customers.findAll(Sort.by(Sort.Direction.DESC, "id")).stream().map(this::toDto).toList();
    }

    @Transactional
    public AdminCustomerDto setCustomerEnabled(Long id, boolean enabled) {
        Customer c = customers.findById(id).orElseThrow(() -> notFound("Customer"));
        c.getUser().setEnabled(enabled);
        users.save(c.getUser());
        if (adminActivityLogService != null) {
            String name = c.getUser().getName() != null ? c.getUser().getName() : "Customer #" + id;
            adminActivityLogService.record(
                    null,
                    enabled ? "CUSTOMER_REINSTATED" : "CUSTOMER_SUSPENDED",
                    "CUSTOMER",
                    id,
                    name,
                    (enabled ? "Reinstated" : "Suspended") + " customer account #" + id + " (" + c.getUser().getEmail() + ")",
                    null,
                    "/admin/customers.html"
            );
        }
        return toDto(c);
    }

    @Transactional(readOnly = true)
    public List<AdminProviderDto> providers(String status) {
        return providers(status, null, null, null, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<AdminProviderDto> providers(
            String status, String search, Long serviceId, String subService,
            String area, Double minRating, Boolean availability, Boolean enabled,
            String sortBy, String sortDir
    ) {
        Specification<Provider> spec = buildProviderSpec(status, search, serviceId, subService, area, minRating, availability, enabled);
        Sort sort = resolveProviderSort(sortBy, sortDir);
        return providers.findAll(spec, sort).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<AdminProviderDto> providersPaged(
            String status, String search, Long serviceId, String subService,
            String area, Double minRating, Boolean availability, Boolean enabled,
            int page, int size, String sortBy, String sortDir
    ) {
        Specification<Provider> spec = buildProviderSpec(status, search, serviceId, subService, area, minRating, availability, enabled);
        Sort sort = resolveProviderSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sort);
        return providers.findAll(spec, pageable).map(this::toDto);
    }

    private Sort resolveProviderSort(String sortBy, String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "id";
        if ("rating".equalsIgnoreCase(sortBy)) {
            field = "rating";
        } else if ("businessName".equalsIgnoreCase(sortBy)) {
            field = "businessName";
        } else if ("createdAt".equalsIgnoreCase(sortBy) || "registeredAt".equalsIgnoreCase(sortBy)) {
            field = "id";
        }
        return Sort.by(dir, field);
    }

    private Specification<Provider> buildProviderSpec(
            String status, String search, Long serviceId, String subService,
            String area, Double minRating, Boolean availability, Boolean enabled
    ) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
                try {
                    VerificationStatus vs = VerificationStatus.valueOf(status.trim().toUpperCase());
                    ps.add(cb.equal(root.get("verificationStatus"), vs));
                } catch (IllegalArgumentException ignored) {}
            }

            if (availability != null) {
                ps.add(cb.equal(root.get("availability"), availability));
            }

            if (enabled != null) {
                ps.add(cb.equal(root.get("user").get("enabled"), enabled));
            }

            if (minRating != null && minRating > 0) {
                ps.add(cb.greaterThanOrEqualTo(root.get("rating"), BigDecimal.valueOf(minRating)));
            }

            if (serviceId != null && serviceId > 0) {
                Join<Provider, ServiceType> sJoin = root.join("services", JoinType.INNER);
                ps.add(cb.equal(sJoin.get("id"), serviceId));
                query.distinct(true);
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate pBiz = cb.like(cb.lower(root.get("businessName")), term);
                Predicate pName = cb.like(cb.lower(root.get("user").get("name")), term);
                Predicate pEmail = cb.like(cb.lower(root.get("user").get("email")), term);
                Predicate pPhone = cb.like(cb.lower(root.get("user").get("phone")), term);
                ps.add(cb.or(pBiz, pName, pEmail, pPhone));
            }

            if (area != null && !area.isBlank()) {
                Subquery<Long> areaSub = query.subquery(Long.class);
                Root<ProviderArea> areaRoot = areaSub.from(ProviderArea.class);
                areaSub.select(areaRoot.get("providerId"));
                String aTerm = "%" + area.trim().toLowerCase() + "%";
                Predicate matchArea = cb.like(cb.lower(areaRoot.get("area")), aTerm);
                Predicate matchCity = cb.like(cb.lower(areaRoot.get("city")), aTerm);
                areaSub.where(cb.and(
                        cb.equal(areaRoot.get("providerId"), root.get("id")),
                        cb.or(matchArea, matchCity)
                ));
                ps.add(cb.exists(areaSub));
            }

            if (subService != null && !subService.isBlank()) {
                Subquery<Long> subSub = query.subquery(Long.class);
                Root<ProviderSubService> subRoot = subSub.from(ProviderSubService.class);
                subSub.select(subRoot.get("providerId"));
                String sTerm = "%" + subService.trim().toLowerCase() + "%";
                Predicate matchSubName = cb.like(cb.lower(subRoot.get("name")), sTerm);
                Predicate matchSubCat = cb.like(cb.lower(subRoot.get("category")), sTerm);
                subSub.where(cb.and(
                        cb.equal(subRoot.get("providerId"), root.get("id")),
                        cb.or(matchSubName, matchSubCat)
                ));
                ps.add(cb.exists(subSub));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public AdminProviderDetailDto getProviderDetails(Long id) {
        Provider p = providers.findById(id).orElseThrow(() -> notFound("Provider"));
        User u = p.getUser();
        ProviderDto pDto = profiles.toDto(p);

        long total = bookings.countByProviderId(id);
        long completed = bookings.countByProviderIdAndStatus(id, BookingStatus.COMPLETED);
        long cancelled = bookings.countByProviderIdAndStatus(id, BookingStatus.CANCELLED);
        long active = bookings.countByProviderIdAndStatusIn(id, ACTIVE_STATUSES);
        boolean quickEligible = p.getVerificationStatus() == VerificationStatus.VERIFIED
                && u.isEnabled()
                && p.isAvailability()
                && p.getServices() != null
                && !p.getServices().isEmpty();

        List<BookingDto> activeList = bookings.findByProviderIdAndStatusInOrderByIdDesc(id, ACTIVE_STATUSES)
                .stream().map(b -> bookingService.toDto(b, "ADMIN")).toList();

        List<ProviderActionLogDto> actionHistory = actionLogs.findByProviderIdOrderByTimestampDesc(id)
                .stream().map(a -> new ProviderActionLogDto(
                        a.getId(), a.getProviderId(), a.getAction(), a.getReason(),
                        a.getActorId(), a.getActorName(), a.getActorRole(), a.getTimestamp()
                )).toList();

        VerificationApplicationDto appDto = null;
        List<VerificationHistoryDto> verHistory = List.of();
        try {
            VerificationApplication app = verificationAppRepo.findTopByProviderIdOrderByIdDesc(id).orElse(null);
            if (app != null) {
                appDto = verificationService.toDto(app);
                if (appDto.history() != null) {
                    verHistory = appDto.history();
                }
            }
        } catch (Exception ignored) {}

        LocalDateTime reg = u.getCreatedAt() != null ? u.getCreatedAt() : p.getCreatedAt();

        return new AdminProviderDetailDto(
                pDto,
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                u.isEnabled(),
                reg,
                total,
                completed,
                cancelled,
                active,
                quickEligible,
                activeList,
                actionHistory,
                verHistory,
                appDto
        );
    }

    @Transactional
    public AdminProviderDto setProviderEnabled(Long id, boolean enabled, String reason, User adminUser) {
        Provider p = providers.findById(id).orElseThrow(() -> notFound("Provider"));
        p.getUser().setEnabled(enabled);
        if (!enabled) p.setAvailability(false);
        users.save(p.getUser());
        Provider saved = providers.save(p);

        String action = enabled ? "REINSTATE" : "SUSPEND";
        String actionReason = StringUtils.hasText(reason) ? reason.trim() : (enabled ? "Account reinstated by admin" : "Account suspended by admin");
        Long actorId = adminUser != null ? adminUser.getId() : null;
        String actorName = adminUser != null ? adminUser.getName() : "ADMIN";
        String actorRole = adminUser != null && adminUser.getRole() != null ? adminUser.getRole().name() : "ROLE_ADMIN";

        actionLogs.save(new ProviderActionLog(id, action, actionReason, actorId, actorName, actorRole));

        if (adminActivityLogService != null) {
            String pName = p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + id;
            adminActivityLogService.record(
                    adminUser,
                    enabled ? "PROVIDER_REINSTATED" : "PROVIDER_SUSPENDED",
                    "PROVIDER",
                    id,
                    pName,
                    (enabled ? "Reinstated" : "Suspended") + " provider account #" + id + " (" + (p.getUser() != null ? p.getUser().getEmail() : "") + ")",
                    actionReason,
                    "/admin/providers.html?search=" + id
            );
        }

        return toDto(saved);
    }

    @Transactional
    public AdminProviderDto setProviderEnabled(Long id, boolean enabled) {
        return setProviderEnabled(id, enabled, null, null);
    }

    @Transactional(readOnly = true)
    public List<ProviderActionLogDto> getProviderActionHistory(Long id) {
        return actionLogs.findByProviderIdOrderByTimestampDesc(id)
                .stream().map(a -> new ProviderActionLogDto(
                        a.getId(), a.getProviderId(), a.getAction(), a.getReason(),
                        a.getActorId(), a.getActorName(), a.getActorRole(), a.getTimestamp()
                )).toList();
    }

    @Transactional(readOnly = true)
    public QuickServiceMonitorSummaryDto getQuickServiceMonitoring() {
        List<Booking> activeQuickBookings = bookings.findByBookingTypeAndStatusInOrderByIdDesc(
                BookingType.QUICK_SERVICE, ACTIVE_STATUSES
        );

        LocalDateTime now = LocalDateTime.now();
        List<QuickServiceRequestMonitorDto> requests = new ArrayList<>();
        long pendingCount = 0;
        long inProgressCount = 0;
        long delayedCount = 0;

        for (Booking b : activeQuickBookings) {
            LocalDateTime created = b.getCreatedAt() != null ? b.getCreatedAt() : now;
            long minutes = Math.max(0, Duration.between(created, now).toMinutes());
            boolean isPending = b.getStatus() == BookingStatus.PENDING;
            boolean isDelayed = (isPending && minutes > 5) || (b.getStatus() == BookingStatus.ON_THE_WAY && minutes > 20);

            if (isPending) pendingCount++;
            else inProgressCount++;
            if (isDelayed) delayedCount++;

            List<ProviderDto> matchingProviders = List.of();
            if (isPending && b.getService() != null) {
                String areaClean = b.getArea() != null ? b.getArea().trim().toLowerCase() : "";
                matchingProviders = providers.match(b.getService().getId(), areaClean, true)
                        .stream().map(profiles::toDto).toList();
            }

            requests.add(new QuickServiceRequestMonitorDto(
                    bookingService.toDto(b, "ADMIN"),
                    isDelayed,
                    minutes,
                    matchingProviders
            ));
        }

        return new QuickServiceMonitorSummaryDto(
                activeQuickBookings.size(),
                pendingCount,
                inProgressCount,
                delayedCount,
                requests
        );
    }

    @Transactional
    public AdminProviderDto editProvider(Long id, ProviderUpdateRequest r) {
        Provider p = providers.findById(id).orElseThrow(() -> notFound("Provider"));
        profiles.update(p.getUser().getEmail(), r); // same validation as the provider's own profile edit
        if (adminActivityLogService != null) {
            String pName = p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + id;
            adminActivityLogService.record(
                    null,
                    "PROVIDER_PROFILE_EDITED",
                    "PROVIDER",
                    p.getId(),
                    pName,
                    "Administrator updated profile for provider " + pName,
                    null,
                    "/admin/providers.html?search=" + p.getId()
            );
        }
        return toDto(p);
    }

    @Transactional
    public void deleteProvider(Long id) {
        Provider p = providers.findById(id).orElseThrow(() -> notFound("Provider"));
        String pName = p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + id;

        if (bookings.existsByProviderIdAndStatusIn(id, ACTIVE_STATUSES)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete provider with active bookings. Please cancel or reassign them first.");
        }

        long bCount = bookings.countByProviderId(id);
        if (bCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete provider with existing booking history. Suspend the account instead.");
        }

        List<VerificationApplication> apps = verificationAppRepo.findByProviderId(id);
        for (VerificationApplication app : apps) {
            verificationAppRepo.delete(app);
        }

        favoriteRepo.deleteByProviderId(id);
        reviews.deleteByProviderId(id);
        actionLogs.deleteByProviderId(id);
        profiles.deleteProviderArtifacts(id);

        p.getServices().clear();
        providers.saveAndFlush(p);

        User u = p.getUser();
        providers.delete(p);
        providers.flush();

        if (u != null) {
            notificationRepo.deleteByRecipientId(u.getId());
            users.delete(u);
            users.flush();
        }

        if (adminActivityLogService != null) {
            adminActivityLogService.record(
                    null,
                    "PROVIDER_DELETED",
                    "PROVIDER",
                    id,
                    pName,
                    "Permanently deleted provider " + pName + " (#" + id + ")",
                    null,
                    "/admin/providers.html"
            );
        }
    }

    @Transactional
    public void deleteVerificationApplication(Long appId) {
        VerificationApplication app = verificationAppRepo.findById(appId)
                .orElseThrow(() -> notFound("Verification Application"));
        verificationAppRepo.delete(app);
    }

    private Specification<Booking> buildBookingSpec(
            String search, String status, String type, Long serviceId,
            String subService, Long providerId, Long customerId, String area,
            LocalDate fromDate, LocalDate toDate) {
        final boolean isActive = "ACTIVE".equalsIgnoreCase(status != null ? status.trim() : "");
        final BookingStatus st;
        if (isActive) {
            st = null;
        } else {
            try {
                st = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim()))
                        ? null : BookingStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown booking status: " + status);
            }
        }

        final BookingType bt;
        try {
            bt = (type == null || type.isBlank() || "ALL".equalsIgnoreCase(type.trim()))
                    ? null : BookingType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown booking type: " + type);
        }

        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (isActive) {
                ps.add(root.get("status").in(ACTIVE_STATUSES));
            } else if (st != null) {
                ps.add(cb.equal(root.get("status"), st));
            }

            if (bt != null) {
                ps.add(cb.equal(root.get("bookingType"), bt));
            }

            if (serviceId != null) {
                ps.add(cb.equal(root.get("service").get("id"), serviceId));
            }

            if (providerId != null) {
                ps.add(cb.equal(root.get("provider").get("id"), providerId));
            }

            if (customerId != null) {
                ps.add(cb.equal(root.get("customer").get("id"), customerId));
            }

            if (StringUtils.hasText(subService)) {
                String sub = "%" + subService.trim().toLowerCase() + "%";
                ps.add(cb.like(cb.lower(root.get("subServiceName")), sub));
            }

            if (StringUtils.hasText(area)) {
                String a = "%" + area.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("area")), a),
                        cb.like(cb.lower(root.get("address")), a),
                        cb.like(cb.lower(root.get("formattedAddress")), a)
                ));
            }

            if (fromDate != null) {
                ps.add(cb.or(
                        cb.greaterThanOrEqualTo(root.get("bookingDate"), fromDate),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay())
                ));
            }

            if (toDate != null) {
                ps.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("bookingDate"), toDate),
                        cb.lessThanOrEqualTo(root.get("createdAt"), toDate.atTime(23, 59, 59))
                ));
            }

            if (StringUtils.hasText(search)) {
                String s = "%" + search.trim().toLowerCase() + "%";
                List<Predicate> searchPreds = new ArrayList<>();

                try {
                    Long searchId = Long.parseLong(search.trim());
                    searchPreds.add(cb.equal(root.get("id"), searchId));
                } catch (NumberFormatException ignored) {}

                searchPreds.add(cb.like(cb.lower(root.get("customer").get("user").get("name")), s));
                searchPreds.add(cb.like(cb.lower(root.get("customer").get("user").get("phone")), s));
                searchPreds.add(cb.like(cb.lower(root.get("customer").get("user").get("email")), s));

                searchPreds.add(cb.like(cb.lower(root.get("provider").get("businessName")), s));
                searchPreds.add(cb.like(cb.lower(root.get("provider").get("user").get("name")), s));
                searchPreds.add(cb.like(cb.lower(root.get("provider").get("user").get("phone")), s));
                searchPreds.add(cb.like(cb.lower(root.get("provider").get("user").get("email")), s));

                searchPreds.add(cb.like(cb.lower(root.get("address")), s));
                searchPreds.add(cb.like(cb.lower(root.get("area")), s));
                searchPreds.add(cb.like(cb.lower(root.get("subServiceName")), s));

                ps.add(cb.or(searchPreds.toArray(new Predicate[0])));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private Sort resolveBookingSort(String sortBy, String sortDir) {
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = sortBy != null ? sortBy.trim() : "id";
        return switch (field) {
            case "createdAt" -> Sort.by(dir, "createdAt").and(Sort.by(dir, "id"));
            case "scheduled", "bookingDate" -> Sort.by(dir, "bookingDate").and(Sort.by(dir, "bookingTime")).and(Sort.by(dir, "id"));
            case "status" -> Sort.by(dir, "status").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(dir, "id");
        };
    }

    @Transactional(readOnly = true)
    public Page<BookingDto> bookingsPaged(
            String search, String status, String type, Long serviceId,
            String subService, Long providerId, Long customerId, String area,
            LocalDate fromDate, LocalDate toDate,
            int page, int size, String sortBy, String sortDir) {
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 15 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveBookingSort(sortBy, sortDir));
        Specification<Booking> spec = buildBookingSpec(
                search, status, type, serviceId, subService, providerId, customerId, area, fromDate, toDate
        );
        return bookings.findAll(spec, pageable).map(b -> bookingService.toDto(b, "ADMIN"));
    }

    @Transactional(readOnly = true)
    public List<BookingDto> bookings(
            String search, String status, String type, Long serviceId,
            String subService, Long providerId, Long customerId, String area,
            LocalDate fromDate, LocalDate toDate, String sortBy, String sortDir) {
        Specification<Booking> spec = buildBookingSpec(
                search, status, type, serviceId, subService, providerId, customerId, area, fromDate, toDate
        );
        return bookings.findAll(spec, resolveBookingSort(sortBy, sortDir))
                .stream().map(b -> bookingService.toDto(b, "ADMIN")).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> bookings(String status, String type, Long serviceId, Long providerId, Long customerId) {
        return bookings(null, status, type, serviceId, null, providerId, customerId, null, null, null, "id", "desc");
    }

    @Transactional(readOnly = true)
    public List<BookingDto> bookings(String status, Long serviceId, Long providerId, Long customerId) {
        return bookings(null, status, null, serviceId, null, providerId, customerId, null, null, null, "id", "desc");
    }

    @Transactional(readOnly = true)
    public AdminBookingDetailDto getBookingDetails(Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> notFound("Booking"));
        BookingDto bDto = bookingService.toDto(b, "ADMIN");

        Customer c = b.getCustomer();
        User cu = c != null ? c.getUser() : null;
        CustomerProfileDto custDto = c != null && cu != null ? new CustomerProfileDto(
                c.getId(), cu.getName(), cu.getEmail(), cu.getPhone(),
                c.getAddress(), c.getArea(), c.getCity(), c.getPincode(),
                c.getFormattedAddress(), c.getLatitude(), c.getLongitude(),
                c.getProfileImage(), null
        ) : null;

        Provider p = b.getProvider();
        ProviderDto provDto = p != null ? profiles.toDto(p) : null;

        ReviewDto reviewDto = null;
        Review rev = reviews.findByBookingId(id).orElse(null);
        if (rev != null && cu != null) {
            reviewDto = new ReviewDto(
                    rev.getId(),
                    cu.getName(),
                    rev.getRating(),
                    rev.getReview(),
                    rev.getCreatedAt(),
                    b.getService() != null ? b.getService().getName() : "Service",
                    true
            );
        }

        List<BookingStatusHistoryDto> history = historyService.getHistoryForBooking(id);

        List<ProviderDto> eligible = List.of();
        if (b.getService() != null && (b.getStatus() == BookingStatus.PENDING || b.getBookingType() == BookingType.QUICK_SERVICE)) {
            String areaClean = b.getArea() != null ? b.getArea().trim().toLowerCase() : "";
            boolean quick = b.getBookingType() == BookingType.QUICK_SERVICE;
            eligible = providers.match(b.getService().getId(), areaClean, quick)
                    .stream().map(profiles::toDto).toList();
        }

        return new AdminBookingDetailDto(
                bDto,
                custDto,
                provDto,
                b.getCancellationReason(),
                bDto.price(),
                bDto.paymentStatus(),
                reviewDto,
                history,
                eligible
        );
    }

    @Transactional
    public AdminBookingDetailDto adminUpdateBooking(Long id, AdminBookingActionRequest req, User adminUser) {
        Booking b = bookings.findWithLockById(id).orElseThrow(() -> notFound("Booking"));
        BookingStatus prev = b.getStatus();
        String reason = req.reason() != null ? req.reason().trim() : "";
        if (!StringUtils.hasText(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An administrative explanation is required for booking modifications");
        }

        Long adminId = adminUser != null ? adminUser.getId() : null;
        String adminName = adminUser != null ? adminUser.getName() : "System Administrator";

        if (req.reassignProviderId() != null && !req.reassignProviderId().equals(b.getProvider().getId())) {
            Provider newProv = providers.findById(req.reassignProviderId())
                    .orElseThrow(() -> notFound("New provider"));
            if (newProv.getVerificationStatus() != VerificationStatus.VERIFIED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign unverified provider");
            }
            if (!newProv.getUser().isEnabled()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign suspended provider");
            }
            Provider oldProv = b.getProvider();
            b.setProvider(newProv);
            historyService.record(
                    id, prev.name(), b.getStatus().name(), "REASSIGN",
                    "Reassigned from " + oldProv.getBusinessName() + " to " + newProv.getBusinessName() + ". Reason: " + reason,
                    adminId, adminName, "ADMIN"
            );
        }

        if (StringUtils.hasText(req.status()) && !req.status().equalsIgnoreCase(prev.name())) {
            BookingStatus targetStatus;
            try {
                targetStatus = BookingStatus.valueOf(req.status().trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + req.status());
            }

            b.setStatus(targetStatus);
            if (targetStatus == BookingStatus.COMPLETED && b.getCompletedAt() == null) {
                b.setCompletedAt(LocalDateTime.now());
            }
            if (targetStatus == BookingStatus.CANCELLED) {
                b.setCancellationReason(reason);
            }

            historyService.record(
                    id, prev.name(), targetStatus.name(), "ADMIN_" + targetStatus.name(),
                    reason, adminId, adminName, "ADMIN"
            );
        }

        Booking saved = bookings.save(b);

        if (adminActivityLogService != null) {
            adminActivityLogService.record(
                    adminUser,
                    "BOOKING_ADMIN_OVERRIDE",
                    "BOOKING",
                    b.getId(),
                    "Booking #BK-" + b.getId(),
                    "Administrator overridden booking status to " + b.getStatus() + (req.reassignProviderId() != null ? " with reassigned provider #" + req.reassignProviderId() : ""),
                    reason,
                    "/admin/bookings.html?bookingId=" + b.getId()
            );
        }

        return getBookingDetails(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<BookingStatusHistoryDto> getBookingHistory(Long id) {
        return historyService.getHistoryForBooking(id);
    }

    @Transactional(readOnly = true)
    public BookingAnalyticsDto getBookingAnalytics(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate != null ? fromDate : LocalDate.now().minusDays(13);
        LocalDate end = toDate != null ? toDate : LocalDate.now();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            byStatus.put(s.name(), bookings.countByStatus(s));
        }

        long total = bookings.count();
        long active = byStatus.getOrDefault("PENDING", 0L)
                + byStatus.getOrDefault("ACCEPTED", 0L)
                + byStatus.getOrDefault("ON_THE_WAY", 0L)
                + byStatus.getOrDefault("SERVICE_STARTED", 0L)
                + byStatus.getOrDefault("AWAITING_COMPLETION", 0L);
        long completed = byStatus.getOrDefault("COMPLETED", 0L);
        long cancelled = byStatus.getOrDefault("CANCELLED", 0L) + byStatus.getOrDefault("REJECTED", 0L);
        long quickCount = bookings.countByBookingType(BookingType.QUICK_SERVICE);
        long normalCount = bookings.countByBookingType(BookingType.NORMAL);

        List<DailyBookingTrendDto> dailyTrends = new ArrayList<>();
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            LocalDate d = curr;
            Specification<Booking> spec = (root, query, cb) -> cb.or(
                    cb.equal(root.get("bookingDate"), d),
                    cb.and(
                            cb.greaterThanOrEqualTo(root.get("createdAt"), d.atStartOfDay()),
                            cb.lessThanOrEqualTo(root.get("createdAt"), d.atTime(23, 59, 59))
                    )
            );
            List<Booking> dayBookings = bookings.findAll(spec);
            long dayTotal = dayBookings.size();
            long dayQuick = dayBookings.stream().filter(b -> b.getBookingType() == BookingType.QUICK_SERVICE).count();
            long dayComp = dayBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
            long dayCanc = dayBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED).count();
            dailyTrends.add(new DailyBookingTrendDto(d, dayTotal, dayQuick, dayComp, dayCanc));
            curr = curr.plusDays(1);
        }

        QuickServiceMonitorSummaryDto qm = getQuickServiceMonitoring();
        long unassigned = qm.requests().stream().filter(r -> r.booking().providerBusinessName() == null).count();

        return new BookingAnalyticsDto(
                total,
                active,
                completed,
                cancelled,
                quickCount,
                normalCount,
                byStatus,
                dailyTrends,
                unassigned,
                qm.delayedCount()
        );
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsDto getFullAnalytics(String period, LocalDate fromDate, LocalDate toDate) {
        LocalDate start;
        LocalDate end;
        String normPeriod = period != null ? period.trim().toLowerCase() : "30d";

        if ("today".equals(normPeriod)) {
            start = LocalDate.now();
            end = LocalDate.now();
        } else if ("7d".equals(normPeriod)) {
            start = LocalDate.now().minusDays(6);
            end = LocalDate.now();
        } else if ("30d".equals(normPeriod)) {
            start = LocalDate.now().minusDays(29);
            end = LocalDate.now();
        } else if ("90d".equals(normPeriod)) {
            start = LocalDate.now().minusDays(89);
            end = LocalDate.now();
        } else if ("custom".equals(normPeriod) || fromDate != null || toDate != null) {
            normPeriod = "custom";
            if (fromDate != null && toDate != null) {
                if (fromDate.isAfter(toDate)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate cannot be after toDate");
                }
                if (ChronoUnit.DAYS.between(fromDate, toDate) > 365) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range cannot exceed 365 days");
                }
                start = fromDate;
                end = toDate;
            } else if (fromDate != null) {
                start = fromDate;
                end = LocalDate.now();
            } else {
                start = LocalDate.now().minusDays(29);
                end = toDate;
            }
        } else {
            normPeriod = "30d";
            start = LocalDate.now().minusDays(29);
            end = LocalDate.now();
        }

        int daysCount = (int) ChronoUnit.DAYS.between(start, end) + 1;
        AnalyticsDateRangeDto dateRangeDto = new AnalyticsDateRangeDto(normPeriod, start, end, daysCount);

        // 1. Overview KPIs
        DashboardDto overview = dashboard();

        // 2. Filtered Period Bookings Specification
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59, 999999999);
        Specification<Booking> periodSpec = (root, query, cb) -> cb.or(
                cb.between(root.get("createdAt"), startDateTime, endDateTime),
                cb.between(root.get("bookingDate"), start, end)
        );
        List<Booking> periodBookings = bookings.findAll(periodSpec);

        // 3. Status Breakdown & Type Comparison
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            statusBreakdown.put(s.name(), 0L);
        }
        long normalCount = 0;
        long quickCount = 0;
        for (Booking b : periodBookings) {
            statusBreakdown.compute(b.getStatus().name(), (k, v) -> v == null ? 1L : v + 1L);
            if (b.getBookingType() == BookingType.QUICK_SERVICE) {
                quickCount++;
            } else {
                normalCount++;
            }
        }
        double normPct = periodBookings.isEmpty() ? 0.0 : Math.round((normalCount * 1000.0) / periodBookings.size()) / 10.0;
        double quickPct = periodBookings.isEmpty() ? 0.0 : Math.round((quickCount * 1000.0) / periodBookings.size()) / 10.0;
        BookingTypeComparisonDto typeComparison = new BookingTypeComparisonDto(normalCount, quickCount, normPct, quickPct);

        // 4. Daily Trends
        List<DailyBookingTrendDto> dailyTrends = new ArrayList<>();
        LocalDate curr = start;
        while (!curr.isAfter(end)) {
            LocalDate d = curr;
            long dTotal = 0, dQuick = 0, dComp = 0, dCanc = 0;
            for (Booking b : periodBookings) {
                LocalDate bDate = b.getCreatedAt() != null ? b.getCreatedAt().toLocalDate() : b.getBookingDate();
                if (d.equals(bDate)) {
                    dTotal++;
                    if (b.getBookingType() == BookingType.QUICK_SERVICE) dQuick++;
                    if (b.getStatus() == BookingStatus.COMPLETED) dComp++;
                    if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) dCanc++;
                }
            }
            dailyTrends.add(new DailyBookingTrendDto(d, dTotal, dQuick, dComp, dCanc));
            curr = curr.plusDays(1);
        }

        // 5. Provider Performance
        Map<Long, long[]> provBookingsMap = new HashMap<>(); // providerId -> [total, completed, cancelled, active]
        for (Booking b : periodBookings) {
            if (b.getProvider() != null && b.getProvider().getId() != null) {
                Long pId = b.getProvider().getId();
                long[] stats = provBookingsMap.computeIfAbsent(pId, k -> new long[4]);
                stats[0]++; // total
                if (b.getStatus() == BookingStatus.COMPLETED) {
                    stats[1]++; // completed
                } else if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED) {
                    stats[2]++; // cancelled
                } else if (ACTIVE_STATUSES.contains(b.getStatus())) {
                    stats[3]++; // active
                }
            }
        }

        List<Provider> allProviders = providers.findAll();
        Map<Long, String> ownerNames = new HashMap<>();
        for (Provider p : allProviders) {
            String name = (p.getUser() != null && p.getUser().getName() != null)
                    ? p.getUser().getName() : (p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + p.getId());
            ownerNames.put(p.getId(), name);
        }

        // Top Rated
        List<TopRatedProviderDto> topRated = allProviders.stream()
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .sorted(Comparator.comparing(Provider::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Provider::getReviewCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(p -> new TopRatedProviderDto(
                        p.getId(),
                        p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + p.getId(),
                        ownerNames.getOrDefault(p.getId(), "Provider"),
                        p.getRating() != null ? p.getRating().doubleValue() : 0.0,
                        p.getReviewCount(),
                        provBookingsMap.containsKey(p.getId()) ? provBookingsMap.get(p.getId())[1] : 0L
                ))
                .toList();

        // Most Booked
        List<TopBookedProviderDto> mostBooked = provBookingsMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue()[0], e1.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Provider p = allProviders.stream().filter(pr -> pr.getId().equals(e.getKey())).findFirst().orElse(null);
                    String bName = p != null && p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + e.getKey();
                    long[] stats = e.getValue();
                    return new TopBookedProviderDto(e.getKey(), bName, ownerNames.getOrDefault(e.getKey(), "Provider"), stats[0], stats[1], stats[3]);
                })
                .toList();

        // Most Completed
        List<TopCompletedProviderDto> mostCompleted = provBookingsMap.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted((e1, e2) -> Long.compare(e2.getValue()[1], e1.getValue()[1]))
                .limit(5)
                .map(e -> {
                    Provider p = allProviders.stream().filter(pr -> pr.getId().equals(e.getKey())).findFirst().orElse(null);
                    String bName = p != null && p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + e.getKey();
                    long[] stats = e.getValue();
                    double rate = stats[0] > 0 ? Math.round((stats[1] * 1000.0) / stats[0]) / 10.0 : 0.0;
                    return new TopCompletedProviderDto(e.getKey(), bName, ownerNames.getOrDefault(e.getKey(), "Provider"), stats[1], rate);
                })
                .toList();

        // Cancellation Rates (min 1 booking)
        List<ProviderCancellationRateDto> cancellationRates = provBookingsMap.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 1 && e.getValue()[2] > 0)
                .sorted((e1, e2) -> {
                    double r1 = (double) e1.getValue()[2] / e1.getValue()[0];
                    double r2 = (double) e2.getValue()[2] / e2.getValue()[0];
                    return Double.compare(r2, r1);
                })
                .limit(5)
                .map(e -> {
                    Provider p = allProviders.stream().filter(pr -> pr.getId().equals(e.getKey())).findFirst().orElse(null);
                    String bName = p != null && p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + e.getKey();
                    long[] stats = e.getValue();
                    double rate = Math.round((stats[2] * 1000.0) / stats[0]) / 10.0;
                    return new ProviderCancellationRateDto(e.getKey(), bName, ownerNames.getOrDefault(e.getKey(), "Provider"), stats[0], stats[2], rate);
                })
                .toList();

        // Inactive Providers (verified but 0 bookings in period)
        List<InactiveProviderDto> inactiveProviders = allProviders.stream()
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED && (!provBookingsMap.containsKey(p.getId()) || provBookingsMap.get(p.getId())[0] == 0))
                .limit(5)
                .map(p -> new InactiveProviderDto(
                        p.getId(),
                        p.getBusinessName() != null ? p.getBusinessName() : "Provider #" + p.getId(),
                        ownerNames.getOrDefault(p.getId(), "Provider"),
                        p.getVerificationStatus().name(),
                        p.isAvailability(),
                        p.getCreatedAt(),
                        bookings.countByProviderId(p.getId())
                ))
                .toList();

        ProviderPerformanceAnalyticsDto providerPerformance = new ProviderPerformanceAnalyticsDto(
                topRated, mostBooked, mostCompleted, cancellationRates, inactiveProviders
        );

        // 6. Service Demand Analytics
        Map<Long, ServiceType> serviceTypeMap = new HashMap<>();
        services.findAll().forEach(st -> serviceTypeMap.put(st.getId(), st));

        long totalPeriodBookings = Math.max(1, periodBookings.size());

        // Popular Categories
        Map<Long, Long> catCounts = new HashMap<>();
        Map<Long, String> catNames = new HashMap<>();
        for (Booking b : periodBookings) {
            if (b.getService() != null && b.getService().getId() != null) {
                Long sId = b.getService().getId();
                catCounts.compute(sId, (k, v) -> v == null ? 1L : v + 1L);
                catNames.putIfAbsent(sId, b.getService().getName() != null ? b.getService().getName() : "Service #" + sId);
            }
        }
        List<CategoryDemandDto> popularCategories = catCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .map(e -> {
                    Long sId = e.getKey();
                    String sName = catNames.getOrDefault(sId, "Service");
                    long cnt = e.getValue();
                    double rev = cnt * 499.0;
                    double share = Math.round((cnt * 1000.0) / totalPeriodBookings) / 10.0;
                    String icon = serviceTypeMap.containsKey(sId) && serviceTypeMap.get(sId).getIcon() != null
                            ? serviceTypeMap.get(sId).getIcon() : "bi-stars";
                    return new CategoryDemandDto(sId, sName, icon, cnt, rev, share);
                })
                .toList();

        // Popular Subservices
        Map<String, Long> subCounts = new HashMap<>();
        Map<String, String> subCatNames = new HashMap<>();
        for (Booking b : periodBookings) {
            if (StringUtils.hasText(b.getSubServiceName())) {
                String sub = b.getSubServiceName().trim();
                subCounts.compute(sub, (k, v) -> v == null ? 1L : v + 1L);
                if (b.getService() != null && b.getService().getName() != null) {
                    subCatNames.putIfAbsent(sub, b.getService().getName());
                }
            }
        }
        List<SubServiceDemandDto> popularSubServices = subCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                .limit(6)
                .map(e -> new SubServiceDemandDto(e.getKey(), subCatNames.getOrDefault(e.getKey(), "General"), e.getValue(), 499.0))
                .toList();

        // Area Demand
        Map<String, long[]> areaCounts = new HashMap<>(); // area -> [total, quick, norm]
        for (Booking b : periodBookings) {
            if (StringUtils.hasText(b.getArea())) {
                String a = b.getArea().trim();
                long[] counts = areaCounts.computeIfAbsent(a, k -> new long[3]);
                counts[0]++;
                if (b.getBookingType() == BookingType.QUICK_SERVICE) counts[1]++;
                else counts[2]++;
            }
        }
        List<AreaDemandDto> areaDemand = areaCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue()[0], e1.getValue()[0]))
                .limit(8)
                .map(e -> {
                    String a = e.getKey();
                    long[] counts = e.getValue();
                    double share = Math.round((counts[0] * 1000.0) / totalPeriodBookings) / 10.0;
                    return new AreaDemandDto(a, counts[0], counts[1], counts[2], share);
                })
                .toList();

        // Supply Gap Analysis
        List<Object[]> areaSupplyRows = providerAreas.countVerifiedAndOnlineProvidersByArea();
        Map<String, long[]> supplyMap = new HashMap<>(); // lowercase area -> [verifiedCount, onlineCount]
        for (Object[] r : areaSupplyRows) {
            if (r[0] != null) {
                String a = r[0].toString().toLowerCase().trim();
                long vCount = r[1] != null ? ((Number) r[1]).longValue() : 0L;
                long oCount = r[2] != null ? ((Number) r[2]).longValue() : 0L;
                supplyMap.put(a, new long[]{vCount, oCount});
            }
        }

        // Map active demand per area
        Map<String, Long> activeDemandPerArea = new HashMap<>();
        for (Booking b : periodBookings) {
            if (b.getArea() != null && ACTIVE_STATUSES.contains(b.getStatus())) {
                activeDemandPerArea.compute(b.getArea().trim(), (k, v) -> v == null ? 1L : v + 1L);
            }
        }

        List<AreaSupplyGapDto> supplyGaps = new ArrayList<>();
        for (AreaDemandDto ad : areaDemand) {
            String aName = ad.area();
            String aKey = aName.toLowerCase().trim();
            long actDemand = activeDemandPerArea.getOrDefault(aName, 0L);
            long vCount = supplyMap.containsKey(aKey) ? supplyMap.get(aKey)[0] : 0L;
            long oCount = supplyMap.containsKey(aKey) ? supplyMap.get(aKey)[1] : 0L;
            String status = "ADEQUATE";
            if (vCount == 0) {
                status = "NO_COVERAGE";
            } else if (oCount == 0 && actDemand > 0) {
                status = "HIGH_DEMAND";
            } else if (actDemand > oCount * 2) {
                status = "HIGH_DEMAND";
            }
            supplyGaps.add(new AreaSupplyGapDto(aName, actDemand, vCount, oCount, status));
        }

        ServiceDemandAnalyticsDto serviceDemand = new ServiceDemandAnalyticsDto(
                popularCategories, popularSubServices, areaDemand, supplyGaps
        );

        // 7. Quick Service Performance
        List<Booking> quickBookings = periodBookings.stream()
                .filter(b -> b.getBookingType() == BookingType.QUICK_SERVICE)
                .toList();

        long qTotal = quickBookings.size();
        long qAssigned = quickBookings.stream().filter(b -> b.getProvider() != null).count();
        long qUnassigned = qTotal - qAssigned;
        long qAccepted = quickBookings.stream().filter(b -> b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.REJECTED).count();
        long qCompleted = quickBookings.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
        long qCancelled = quickBookings.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.REJECTED).count();

        // Calculate actual dispatch times from BookingStatusHistory
        List<Long> qIds = quickBookings.stream().map(Booking::getId).toList();
        List<BookingStatusHistory> qHistories = qIds.isEmpty() ? List.of() : historyRepo.findByBookingIdInOrderByBookingIdAscTimestampAsc(qIds);
        Map<Long, List<BookingStatusHistory>> qHistoryMap = new HashMap<>();
        for (BookingStatusHistory h : qHistories) {
            qHistoryMap.computeIfAbsent(h.getBookingId(), k -> new ArrayList<>()).add(h);
        }

        List<Double> assignmentTimes = new ArrayList<>();
        List<Double> arrivalTimes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        long delayedCount = 0;

        for (Booking b : quickBookings) {
            LocalDateTime created = b.getCreatedAt();
            List<BookingStatusHistory> bList = qHistoryMap.getOrDefault(b.getId(), List.of());
            LocalDateTime acceptedTime = null;
            LocalDateTime transitTime = null;
            LocalDateTime startTime = null;

            for (BookingStatusHistory h : bList) {
                if (created == null && "CREATE".equalsIgnoreCase(h.getAction())) {
                    created = h.getTimestamp();
                }
                if (acceptedTime == null && ("ACCEPTED".equalsIgnoreCase(h.getToStatus()) || (h.getAction() != null && h.getAction().contains("ACCEPT")))) {
                    acceptedTime = h.getTimestamp();
                }
                if (transitTime == null && "ON_THE_WAY".equalsIgnoreCase(h.getToStatus())) {
                    transitTime = h.getTimestamp();
                }
                if (startTime == null && ("SERVICE_STARTED".equalsIgnoreCase(h.getToStatus()) || "START_SERVICE".equalsIgnoreCase(h.getAction()))) {
                    startTime = h.getTimestamp();
                }
            }

            if (created != null && acceptedTime != null && !acceptedTime.isBefore(created)) {
                double mins = Math.max(0.1, Duration.between(created, acceptedTime).toSeconds() / 60.0);
                if (mins <= 1440) assignmentTimes.add(mins);
            }

            LocalDateTime departRef = transitTime != null ? transitTime : acceptedTime;
            if (departRef != null && startTime != null && !startTime.isBefore(departRef)) {
                double mins = Math.max(0.1, Duration.between(departRef, startTime).toSeconds() / 60.0);
                if (mins <= 1440) arrivalTimes.add(mins);
            }

            // Delayed check (>15m without assignment)
            if (b.getStatus() == BookingStatus.PENDING && created != null) {
                if (Duration.between(created, now).toMinutes() > 15) {
                    delayedCount++;
                }
            }
        }

        Double avgAssignmentMinutes = assignmentTimes.isEmpty() ? null
                : Math.round((assignmentTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;
        Double avgArrivalMinutes = arrivalTimes.isEmpty() ? null
                : Math.round((arrivalTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)) * 10.0) / 10.0;

        QuickServiceAnalyticsDto quickServicePerformance = new QuickServiceAnalyticsDto(
                qTotal, qAssigned, qUnassigned, qAccepted, qCompleted, qCancelled,
                avgAssignmentMinutes, assignmentTimes.size(),
                avgArrivalMinutes, arrivalTimes.size(),
                delayedCount
        );

        return new AdminAnalyticsDto(
                overview,
                dateRangeDto,
                dailyTrends,
                statusBreakdown,
                typeComparison,
                providerPerformance,
                serviceDemand,
                quickServicePerformance
        );
    }

    private AdminCustomerDto toDto(Customer c) {
        User u = c.getUser();
        return new AdminCustomerDto(c.getId(), u.getName(), u.getEmail(), u.getPhone(), c.getAddress(), c.getArea(),
                c.getCity(), c.getPincode(), u.isEnabled(), bookings.countByCustomerId(c.getId()));
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private AdminProviderDto toDto(Provider p) {
        User u = p.getUser();
        long total = bookings.countByProviderId(p.getId());
        long completed = bookings.countByProviderIdAndStatus(p.getId(), BookingStatus.COMPLETED);
        long cancelled = bookings.countByProviderIdAndStatus(p.getId(), BookingStatus.CANCELLED);
        long active = bookings.countByProviderIdAndStatusIn(p.getId(), ACTIVE_STATUSES);
        boolean quickEligible = p.getVerificationStatus() == VerificationStatus.VERIFIED
                && u.isEnabled()
                && p.isAvailability()
                && p.getServices() != null
                && !p.getServices().isEmpty();

        LocalDateTime reg = u.getCreatedAt() != null ? u.getCreatedAt() : p.getCreatedAt();

        return new AdminProviderDto(
                profiles.toDto(p),
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                u.isEnabled(),
                reg,
                total,
                completed,
                cancelled,
                active,
                quickEligible
        );
    }

    private static VerificationStatus parse(String s) {
        if (s == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status cannot be null");
        try {
            return VerificationStatus.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be PENDING, VERIFIED or REJECTED");
        }
    }
}
