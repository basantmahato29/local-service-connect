package com.localserviceconnect.dto;

import com.localserviceconnect.entity.BookingType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class Dtos {

    public record AreaDto(
            String area,
            String city
    ) {}

    public record SubServiceDto(
            Long id,
            Long serviceId,
            String serviceName,
            String name,
            String description,
            BigDecimal basePrice,
            String duration,
            String imageUrl,
            boolean enabled,
            int displayOrder
    ) {}

    public record SubServiceCreateRequest(
            @NotNull(message = "Service ID is required")
            Long serviceId,
            @NotBlank(message = "Subservice name is required")
            String name,
            String description,
            BigDecimal basePrice,
            String duration,
            String imageUrl,
            Boolean enabled,
            Integer displayOrder
    ) {}

    public record SubServiceUpdateRequest(
            String name,
            String description,
            BigDecimal basePrice,
            String duration,
            String imageUrl,
            Boolean enabled,
            Integer displayOrder
    ) {}

    public record ServiceDto(
            Long id,
            String name,
            String description,
            String icon,
            String status,
            List<SubServiceDto> subServices
    ) {
        public ServiceDto(Long id, String name, String description, String icon, String status) {
            this(id, name, description, icon, status, List.of());
        }
    }

    public record ProviderSubServiceDto(
            Long id,
            Long providerId,
            String name,
            String category,
            String description,
            BigDecimal price,
            String duration,
            String imageUrl,
            boolean enabled
    ) {}

    public record ProviderPortfolioDto(
            Long id,
            Long providerId,
            String title,
            String subService,
            String beforeImageUrl,
            String afterImageUrl,
            String description,
            String completedDate
    ) {}

    public record ProviderVideoDto(
            Long id,
            Long providerId,
            String title,
            String subService,
            String videoUrl,
            String thumbnailUrl,
            String duration,
            String description
    ) {}

    public record ProviderDto(
            Long id,
            String businessName,
            String description,
            Integer experienceYears,
            BigDecimal approxPrice,
            String verificationStatus,
            boolean availability,
            BigDecimal rating,
            Integer reviewCount,
            List<ServiceDto> services,
            List<AreaDto> areas,
            String formattedAddress,
            Double latitude,
            Double longitude,
            Double serviceRadiusKm,
            Double distanceKm,
            String profileImage,
            String workingHours,
            Integer completedJobsCount,
            List<ProviderSubServiceDto> subServices,
            List<ProviderPortfolioDto> portfolioItems,
            List<ProviderVideoDto> workVideos,
            String providerName,
            String providerEmail,
            String providerPhone,
            Boolean isDemo
    ) {
        public ProviderDto(Long id, String businessName, String description, Integer experienceYears,
                           BigDecimal approxPrice, String verificationStatus, boolean availability,
                           BigDecimal rating, Integer reviewCount, List<ServiceDto> services, List<AreaDto> areas) {
            this(id, businessName, description, experienceYears, approxPrice, verificationStatus,
                    availability, rating, reviewCount, services, areas, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, false);
        }

        public ProviderDto(Long id, String businessName, String description, Integer experienceYears,
                           BigDecimal approxPrice, String verificationStatus, boolean availability,
                           BigDecimal rating, Integer reviewCount, List<ServiceDto> services, List<AreaDto> areas,
                           String formattedAddress, Double latitude, Double longitude, Double serviceRadiusKm, Double distanceKm) {
            this(id, businessName, description, experienceYears, approxPrice, verificationStatus,
                    availability, rating, reviewCount, services, areas, formattedAddress, latitude, longitude,
                    serviceRadiusKm, distanceKm, null, null, null, null, null, null, null, null, null, false);
        }

        public ProviderDto(Long id, String businessName, String description, Integer experienceYears,
                           BigDecimal approxPrice, String verificationStatus, boolean availability,
                           BigDecimal rating, Integer reviewCount, List<ServiceDto> services, List<AreaDto> areas,
                           String formattedAddress, Double latitude, Double longitude, Double serviceRadiusKm, Double distanceKm,
                           String profileImage, String workingHours, Integer completedJobsCount,
                           List<ProviderSubServiceDto> subServices, List<ProviderPortfolioDto> portfolioItems,
                           List<ProviderVideoDto> workVideos) {
            this(id, businessName, description, experienceYears, approxPrice, verificationStatus,
                    availability, rating, reviewCount, services, areas, formattedAddress, latitude, longitude,
                    serviceRadiusKm, distanceKm, profileImage, workingHours, completedJobsCount,
                    subServices, portfolioItems, workVideos, null, null, null, false);
        }
    }

    public record ProviderUpdateRequest(
            String name,
            String phone,
            String businessName,
            String description,
            Integer experienceYears,
            BigDecimal approxPrice,
            List<Long> serviceIds,
            List<AreaDto> areas,
            String formattedAddress,
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90.0 and 90.0")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90.0 and 90.0")
            Double latitude,
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180.0 and 180.0")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180.0 and 180.0")
            Double longitude,
            Double serviceRadiusKm,
            String profileImage,
            String workingHours,
            List<ProviderSubServiceDto> subServices,
            List<ProviderPortfolioDto> portfolioItems,
            List<ProviderVideoDto> workVideos
    ) {
        public ProviderUpdateRequest(String businessName, String description, Integer experienceYears,
                                     BigDecimal approxPrice, List<Long> serviceIds, List<AreaDto> areas) {
            this(null, null, businessName, description, experienceYears, approxPrice, serviceIds, areas, null, null, null, null, null, null, null, null, null);
        }

        public ProviderUpdateRequest(String businessName, String description, Integer experienceYears,
                                     BigDecimal approxPrice, List<Long> serviceIds, List<AreaDto> areas,
                                     String formattedAddress, Double latitude, Double longitude, Double serviceRadiusKm) {
            this(null, null, businessName, description, experienceYears, approxPrice, serviceIds, areas, formattedAddress, latitude, longitude, serviceRadiusKm, null, null, null, null, null);
        }
    }

    public record AvailabilityRequest(
            boolean available
    ) {}

    public record ServiceCreateRequest(
            String name,
            String description,
            String icon
    ) {}

    public record ServiceUpdateRequest(
            String name,
            String description,
            String icon,
            String status
    ) {}

    public record BookingRequest(
            @NotNull(message = "Provider ID is required")
            Long providerId,
            @NotNull(message = "Service ID is required")
            Long serviceId,
            Long subServiceId,
            String subServiceName,
            @NotNull(message = "Booking type is required")
            BookingType bookingType,
            LocalDate bookingDate,
            LocalTime bookingTime,
            String address,
            String area,
            String notes,
            String formattedAddress,
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90.0 and 90.0")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90.0 and 90.0")
            Double latitude,
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180.0 and 180.0")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180.0 and 180.0")
            Double longitude
    ) {
        public BookingRequest(Long providerId, Long serviceId, BookingType bookingType,
                              LocalDate bookingDate, LocalTime bookingTime,
                              String address, String area, String notes) {
            this(providerId, serviceId, null, null, bookingType, bookingDate, bookingTime, address, area, notes, null, null, null);
        }

        public BookingRequest(Long providerId, Long serviceId, BookingType bookingType,
                              LocalDate bookingDate, LocalTime bookingTime,
                              String address, String area, String notes,
                              String formattedAddress, Double latitude, Double longitude) {
            this(providerId, serviceId, null, null, bookingType, bookingDate, bookingTime, address, area, notes, formattedAddress, latitude, longitude);
        }
    }

    public record BookingDto(
            Long id,
            String serviceName,
            Long subServiceId,
            String subServiceName,
            Long providerId,
            String providerBusinessName,
            String businessName,
            String customerName,
            String address,
            String area,
            String bookingType,
            LocalDate bookingDate,
            LocalTime bookingTime,
            String status,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            String customerPhone,
            String providerPhone,
            boolean reviewed,
            String formattedAddress,
            Double latitude,
            Double longitude,
            Double providerLatitude,
            Double providerLongitude,
            Double distanceKm,
            String cancellationReason,
            BigDecimal price,
            String paymentStatus,
            String customerEmail,
            String providerEmail
    ) {
        public BookingDto(Long id, String serviceName, Long providerId, String providerBusinessName,
                          String businessName, String customerName, String address, String area,
                          String bookingType, LocalDate bookingDate, LocalTime bookingTime,
                          String status, String notes, LocalDateTime createdAt, LocalDateTime completedAt,
                          String customerPhone, String providerPhone, boolean reviewed) {
            this(id, serviceName, null, null, providerId, providerBusinessName, businessName, customerName,
                    address, area, bookingType, bookingDate, bookingTime, status, notes, createdAt,
                    completedAt, customerPhone, providerPhone, reviewed, null, null, null, null, null, null,
                    null, null, null, null, null);
        }

        public BookingDto(Long id, String serviceName, Long providerId, String providerBusinessName,
                          String businessName, String customerName, String address, String area,
                          String bookingType, LocalDate bookingDate, LocalTime bookingTime,
                          String status, String notes, LocalDateTime createdAt, LocalDateTime completedAt,
                          String customerPhone, String providerPhone, boolean reviewed,
                          String formattedAddress, Double latitude, Double longitude,
                          Double providerLatitude, Double providerLongitude, Double distanceKm) {
            this(id, serviceName, null, null, providerId, providerBusinessName, businessName, customerName,
                    address, area, bookingType, bookingDate, bookingTime, status, notes, createdAt,
                    completedAt, customerPhone, providerPhone, reviewed, formattedAddress, latitude, longitude,
                    providerLatitude, providerLongitude, distanceKm, null, null, null, null, null);
        }

        public BookingDto(Long id, String serviceName, Long subServiceId, String subServiceName,
                          Long providerId, String providerBusinessName, String businessName, String customerName,
                          String address, String area, String bookingType, LocalDate bookingDate, LocalTime bookingTime,
                          String status, String notes, LocalDateTime createdAt, LocalDateTime completedAt,
                          String customerPhone, String providerPhone, boolean reviewed,
                          String formattedAddress, Double latitude, Double longitude,
                          Double providerLatitude, Double providerLongitude, Double distanceKm) {
            this(id, serviceName, subServiceId, subServiceName, providerId, providerBusinessName, businessName, customerName,
                    address, area, bookingType, bookingDate, bookingTime, status, notes, createdAt,
                    completedAt, customerPhone, providerPhone, reviewed, formattedAddress, latitude, longitude,
                    providerLatitude, providerLongitude, distanceKm, null, null, null, null, null);
        }
    }

    public record StatusRequest(
            String status
    ) {}

    public record OtpResponse(
            String otp,
            LocalDateTime expiresAt,
            long remainingSeconds,
            boolean verified,
            boolean expired,
            String message
    ) {}

    public record OtpVerifyRequest(
            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^[0-9]{4}$", message = "OTP must be a 4-digit number")
            String otp
    ) {}

    public record CustomerProfileDto(
            Long id,
            String name,
            String email,
            String phone,
            String address,
            String area,
            String city,
            String pincode,
            String formattedAddress,
            Double latitude,
            Double longitude,
            String profileImage,
            String token
    ) {
        public CustomerProfileDto(Long id, String name, String email, String phone,
                                  String address, String area, String city, String pincode) {
            this(id, name, email, phone, address, area, city, pincode, null, null, null, null, null);
        }
        public CustomerProfileDto(Long id, String name, String email, String phone,
                                  String address, String area, String city, String pincode,
                                  String formattedAddress, Double latitude, Double longitude) {
            this(id, name, email, phone, address, area, city, pincode, formattedAddress, latitude, longitude, null, null);
        }
        public CustomerProfileDto(Long id, String name, String email, String phone,
                                  String address, String area, String city, String pincode,
                                  String formattedAddress, Double latitude, Double longitude, String profileImage) {
            this(id, name, email, phone, address, area, city, pincode, formattedAddress, latitude, longitude, profileImage, null);
        }
    }

    public record CustomerProfileUpdateRequest(
            String name,
            String phone,
            String address,
            String area,
            String city,
            String pincode,
            String formattedAddress,
            @DecimalMin(value = "-90.0", message = "Latitude must be between -90.0 and 90.0")
            @DecimalMax(value = "90.0", message = "Latitude must be between -90.0 and 90.0")
            Double latitude,
            @DecimalMin(value = "-180.0", message = "Longitude must be between -180.0 and 180.0")
            @DecimalMax(value = "180.0", message = "Longitude must be between -180.0 and 180.0")
            Double longitude,
            String profileImage,
            String email
    ) {
        public CustomerProfileUpdateRequest(String name, String phone, String address,
                                            String area, String city, String pincode) {
            this(name, phone, address, area, city, pincode, null, null, null, null, null);
        }
        public CustomerProfileUpdateRequest(String name, String phone, String address,
                                            String area, String city, String pincode,
                                            String formattedAddress, Double latitude, Double longitude) {
            this(name, phone, address, area, city, pincode, formattedAddress, latitude, longitude, null, null);
        }
    }

    public record ChangePasswordRequest(
            @jakarta.validation.constraints.NotBlank(message = "Current password is required")
            String currentPassword,
            @jakarta.validation.constraints.NotBlank(message = "New password is required")
            @jakarta.validation.constraints.Size(min = 6, message = "New password must be at least 6 characters")
            String newPassword
    ) {}

    public record MapsConfigDto(
            String apiKey,
            boolean configured,
            double defaultLat,
            double defaultLng,
            int defaultZoom
    ) {}

    public record DashboardDto(
            long totalCustomers,
            long totalProviders,
            long verifiedProviders,
            long pendingProviders,
            long totalServices,
            long totalBookings,
            long activeBookings,
            long completedBookings,
            long cancelledBookings,
            long quickServiceBookings,
            java.util.Map<String, Long> bookingsByStatus
    ) {}

    public record AdminProviderDto(
            ProviderDto provider,
            String ownerName,
            String email,
            String phone,
            boolean enabled,
            LocalDateTime createdAt,
            long totalBookings,
            long completedBookings,
            long cancelledBookings,
            long activeBookings,
            boolean quickEligible
    ) {
        public AdminProviderDto(ProviderDto provider, String ownerName, String email, String phone, boolean enabled) {
            this(provider, ownerName, email, phone, enabled, null, 0, 0, 0, 0, false);
        }
    }

    public record ProviderActionLogDto(
            Long id,
            Long providerId,
            String action,
            String reason,
            Long actorId,
            String actorName,
            String actorRole,
            LocalDateTime timestamp
    ) {}

    public record AdminProviderDetailDto(
            ProviderDto provider,
            String ownerName,
            String email,
            String phone,
            boolean enabled,
            LocalDateTime registeredAt,
            long totalBookings,
            long completedBookings,
            long cancelledBookings,
            long activeBookings,
            boolean quickEligible,
            List<BookingDto> activeBookingsList,
            List<ProviderActionLogDto> actionHistory,
            List<VerificationHistoryDto> verificationHistory,
            VerificationApplicationDto verificationApplication
    ) {}

    public record QuickServiceRequestMonitorDto(
            BookingDto booking,
            boolean delayedAssignment,
            long minutesSinceCreated,
            List<ProviderDto> eligibleProviders
    ) {}

    public record QuickServiceMonitorSummaryDto(
            long totalActiveRequests,
            long pendingAssignmentCount,
            long inProgressCount,
            long delayedCount,
            List<QuickServiceRequestMonitorDto> requests
    ) {}

    public record BookingStatusHistoryDto(
            Long id,
            Long bookingId,
            String fromStatus,
            String toStatus,
            String action,
            String reason,
            Long actorId,
            String actorName,
            String actorRole,
            LocalDateTime timestamp
    ) {}

    public record AdminBookingDetailDto(
            BookingDto booking,
            CustomerProfileDto customer,
            ProviderDto provider,
            String cancellationReason,
            BigDecimal price,
            String paymentStatus,
            ReviewDto review,
            List<BookingStatusHistoryDto> history,
            List<ProviderDto> eligibleProviders
    ) {}

    public record AdminBookingActionRequest(
            String status,
            String reason,
            Long reassignProviderId
    ) {}

    public record DailyBookingTrendDto(
            LocalDate date,
            long total,
            long quickService,
            long completed,
            long cancelled
    ) {}

    public record BookingAnalyticsDto(
            long totalBookings,
            long activeBookings,
            long completedBookings,
            long cancelledBookings,
            long quickServiceCount,
            long normalCount,
            Map<String, Long> bookingsByStatus,
            List<DailyBookingTrendDto> dailyTrends,
            long unassignedQuickCount,
            long delayedQuickCount
    ) {}

    public record AnalyticsDateRangeDto(
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            int daysCount
    ) {}

    public record BookingTypeComparisonDto(
            long normalCount,
            long quickServiceCount,
            double normalPercentage,
            double quickPercentage
    ) {}

    public record TopRatedProviderDto(
            Long providerId,
            String businessName,
            String ownerName,
            Double rating,
            Integer reviewCount,
            Long completedBookings
    ) {}

    public record TopBookedProviderDto(
            Long providerId,
            String businessName,
            String ownerName,
            Long totalBookings,
            Long completedBookings,
            Long activeBookings
    ) {}

    public record TopCompletedProviderDto(
            Long providerId,
            String businessName,
            String ownerName,
            Long completedBookings,
            Double completionRate
    ) {}

    public record ProviderCancellationRateDto(
            Long providerId,
            String businessName,
            String ownerName,
            Long totalBookings,
            Long cancelledBookings,
            Double cancellationRate
    ) {}

    public record InactiveProviderDto(
            Long providerId,
            String businessName,
            String ownerName,
            String verificationStatus,
            boolean availability,
            LocalDateTime registeredAt,
            Long lifetimeBookings
    ) {}

    public record ProviderPerformanceAnalyticsDto(
            List<TopRatedProviderDto> topRated,
            List<TopBookedProviderDto> mostBooked,
            List<TopCompletedProviderDto> mostCompleted,
            List<ProviderCancellationRateDto> cancellationRates,
            List<InactiveProviderDto> inactiveProviders
    ) {}

    public record CategoryDemandDto(
            Long serviceId,
            String name,
            String icon,
            Long bookingCount,
            Double estimatedRevenue,
            Double sharePercentage
    ) {}

    public record SubServiceDemandDto(
            String name,
            String categoryName,
            Long bookingCount,
            Double averagePrice
    ) {}

    public record AreaDemandDto(
            String area,
            Long totalBookings,
            Long quickBookings,
            Long normalBookings,
            Double sharePercentage
    ) {}

    public record AreaSupplyGapDto(
            String area,
            Long activeDemand,
            Long verifiedProvidersCount,
            Long onlineProvidersCount,
            String supplyStatus
    ) {}

    public record ServiceDemandAnalyticsDto(
            List<CategoryDemandDto> popularCategories,
            List<SubServiceDemandDto> popularSubServices,
            List<AreaDemandDto> areaDemand,
            List<AreaSupplyGapDto> supplyGaps
    ) {}

    public record QuickServiceAnalyticsDto(
            long totalRequests,
            long assignedRequests,
            long unassignedRequests,
            long acceptedRequests,
            long completedRequests,
            long cancelledRequests,
            Double avgAssignmentMinutes,
            Integer assignmentSampleCount,
            Double avgArrivalMinutes,
            Integer arrivalSampleCount,
            long delayedCount
    ) {}

    public record AdminAnalyticsDto(
            DashboardDto overview,
            AnalyticsDateRangeDto dateRange,
            List<DailyBookingTrendDto> dailyTrends,
            Map<String, Long> statusBreakdown,
            BookingTypeComparisonDto typeComparison,
            ProviderPerformanceAnalyticsDto providerPerformance,
            ServiceDemandAnalyticsDto serviceDemand,
            QuickServiceAnalyticsDto quickServicePerformance
    ) {}

    public record AdminCustomerDto(
            Long id,
            String name,
            String email,
            String phone,
            String address,
            String area,
            String city,
            String pincode,
            boolean enabled,
            long bookingsCount
    ) {
        public long totalBookings() { return bookingsCount; }
    }

    public record ReviewRequest(
            @jakarta.validation.constraints.NotNull(message = "Booking ID is required")
            Long bookingId,
            @jakarta.validation.constraints.NotNull(message = "Rating is required")
            @jakarta.validation.constraints.Min(value = 1, message = "Rating must be at least 1")
            @jakarta.validation.constraints.Max(value = 5, message = "Rating must be at most 5")
            Integer rating,
            @jakarta.validation.constraints.Size(max = 1000, message = "Review text cannot exceed 1000 characters")
            String review,
            Long providerId
    ) {
        public ReviewRequest(Long bookingId, Integer rating, String review) {
            this(bookingId, rating, review, null);
        }
    }

    public record ReviewDto(
            Long id,
            String customerFirstName,
            Integer rating,
            String review,
            LocalDateTime createdAt,
            String serviceName,
            boolean verifiedBooking
    ) {
        public ReviewDto(Long id, String customerFirstName, Integer rating, String review, LocalDateTime createdAt) {
            this(id, customerFirstName, rating, review, createdAt, null, true);
        }
    }

    public record AdminReviewDto(
            Long id,
            Long bookingId,
            String customerName,
            String providerBusinessName,
            Integer rating,
            String review,
            LocalDateTime createdAt
    ) {
        public String businessName() { return providerBusinessName; }
    }

    public record VerifyRequest(
            String status
    ) {}

    public record EnabledRequest(
            boolean enabled
    ) {}

    public record NotificationDto(
            Long id,
            String title,
            String message,
            String type,
            Long bookingId,
            String serviceName,
            String customerName,
            String location,
            String bookingType,
            LocalDate bookingDate,
            LocalTime bookingTime,
            BigDecimal price,
            String bookingStatus,
            boolean read,
            LocalDateTime createdAt,
            String targetUrl,
            String entityType,
            Long entityId,
            String priority
    ) {
        public NotificationDto(Long id, String title, String message, String type, Long bookingId,
                               String serviceName, String customerName, String location, String bookingType,
                               LocalDate bookingDate, LocalTime bookingTime, BigDecimal price,
                               String bookingStatus, boolean read, LocalDateTime createdAt) {
            this(id, title, message, type, bookingId, serviceName, customerName, location, bookingType,
                 bookingDate, bookingTime, price, bookingStatus, read, createdAt, null, null, null, "NORMAL");
        }
    }

    public record AdminActivityLogDto(
            Long id,
            Long actorId,
            String actorName,
            String actorEmail,
            String actorRole,
            String actionType,
            String entityType,
            Long entityId,
            String entityIdentifier,
            String details,
            String reason,
            String result,
            String ipAddress,
            String targetUrl,
            LocalDateTime timestamp
    ) {}

    public record VerificationDocumentDto(
            Long id,
            Long applicationId,
            String documentType,
            String fileName,
            String contentType,
            Long fileSizeBytes,
            LocalDateTime uploadedAt,
            boolean isVerified,
            String adminComment,
            String downloadUrl
    ) {}

    public record VerificationChecklistDto(
            Long id,
            Long applicationId,
            boolean identityDocReceived,
            boolean identityDocReadable,
            boolean nameAndDetailsReviewed,
            boolean identityCheckAuthorized,
            boolean businessInfoReviewed,
            boolean shopPhotosReviewed,
            boolean businessAddressReviewed,
            boolean locationProofReviewed,
            boolean contactVerified,
            boolean allChecked,
            String lastUpdatedByName,
            LocalDateTime updatedAt
    ) {}

    public record VerificationHistoryDto(
            Long id,
            Long applicationId,
            String fromStatus,
            String toStatus,
            String actorName,
            String actorRole,
            String action,
            String notes,
            LocalDateTime timestamp
    ) {}

    public record VerificationApplicationDto(
            Long id,
            Long providerId,
            String providerBusinessName,
            String providerOwnerName,
            String providerEmail,
            String providerPhone,
            String referenceNumber,
            String providerType,
            String status,
            String legalName,
            LocalDate dateOfBirth,
            String idType,
            String idNumberMasked,
            boolean privacyConsent,
            String serviceAddress,
            String city,
            String locality,
            String pincode,
            String businessName,
            String businessType,
            String businessAddress,
            String businessPhone,
            String businessDescription,
            String operatingHours,
            String servicesOffered,
            String serviceAreas,
            String locationProofType,
            String adminNotes,
            String actionRequiredReason,
            String actionRequiredFields,
            String rejectionReason,
            String reviewedByName,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<VerificationDocumentDto> documents,
            VerificationChecklistDto checklist,
            List<VerificationHistoryDto> history
    ) {}

    public record SaveVerificationDraftRequest(
            String providerType,
            String legalName,
            LocalDate dateOfBirth,
            String idType,
            String idNumber,
            Boolean privacyConsent,
            String serviceAddress,
            String city,
            String locality,
            String pincode,
            String businessName,
            String businessType,
            String businessAddress,
            String businessPhone,
            String businessDescription,
            String operatingHours,
            String servicesOffered,
            String serviceAreas,
            String locationProofType
    ) {}

    public record SubmitVerificationRequest(
            String providerType,
            String legalName,
            LocalDate dateOfBirth,
            String idType,
            String idNumber,
            Boolean privacyConsent,
            String serviceAddress,
            String city,
            String locality,
            String pincode,
            String businessName,
            String businessType,
            String businessAddress,
            String businessPhone,
            String businessDescription,
            String operatingHours,
            String servicesOffered,
            String serviceAreas,
            String locationProofType
    ) {}

    public record AdminChecklistUpdateRequest(
            Boolean identityDocReceived,
            Boolean identityDocReadable,
            Boolean nameAndDetailsReviewed,
            Boolean identityCheckAuthorized,
            Boolean businessInfoReviewed,
            Boolean shopPhotosReviewed,
            Boolean businessAddressReviewed,
            Boolean locationProofReviewed,
            Boolean contactVerified
    ) {}

    public record AdminVerificationActionRequest(
            String action, // START_REVIEW, APPROVE, REQUEST_INFO, REJECT
            String reason, // Rejection reason or Action Required explanation
            String fields, // affected fields / docs for ACTION_REQUIRED
            String adminNotes
    ) {}

    public record FavoriteDto(
            Long id,
            Long providerId,
            ProviderDto provider,
            boolean notifyOnAvailable,
            java.time.LocalDateTime createdAt
    ) {}

    public record FavoriteStatusDto(
            Long providerId,
            boolean isFavorite,
            boolean notifyOnAvailable
    ) {}

    public record FavoriteIdsResponse(
            java.util.List<Long> favoriteProviderIds
    ) {}

    public record ToggleFavoriteResponse(
            Long providerId,
            boolean isFavorite,
            String message
    ) {}

    public record UpdateFavoriteNotifyRequest(
            boolean notifyOnAvailable
    ) {}
}
