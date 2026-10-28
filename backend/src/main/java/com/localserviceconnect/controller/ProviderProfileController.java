package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.ProviderProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;

    public ProviderProfileController(ProviderProfileService providerProfileService) {
        this.providerProfileService = providerProfileService;
    }

    /** Public search for verified providers */
    @GetMapping({"/api/providers", "/api/providers/search"})
    public ResponseEntity<List<ProviderDto>> search(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String subService,
            @RequestParam(required = false) Long subServiceId,
            @RequestParam(required = false) String area) {
        return ResponseEntity.ok(providerProfileService.search(serviceId, service, subService, subServiceId, area));
    }

    /** Matching engine for Normal Booking and Quick Service, optionally enhanced with geolocation */
    @GetMapping({"/api/providers/matching", "/api/providers/match"})
    public ResponseEntity<List<ProviderDto>> match(
            @RequestParam long serviceId,
            @RequestParam(required = false) String subService,
            @RequestParam(required = false) Long subServiceId,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "NORMAL") com.localserviceconnect.entity.BookingType type) {
        if ((latitude != null || longitude != null) && !com.localserviceconnect.util.GeoUtils.isValidCoordinates(latitude, longitude)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
        }
        boolean quick = type == com.localserviceconnect.entity.BookingType.QUICK_SERVICE;
        return ResponseEntity.ok(providerProfileService.match(serviceId, subService, subServiceId, area, latitude, longitude, quick));
    }

    /** Find nearby verified providers based on geolocation coordinates and service coverage radius */
    @GetMapping("/api/providers/nearby")
    public ResponseEntity<List<ProviderDto>> nearby(
            @RequestParam(required = false) Long serviceId,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "NORMAL") com.localserviceconnect.entity.BookingType type) {
        if (!com.localserviceconnect.util.GeoUtils.isValidCoordinates(latitude, longitude)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
        }
        if (radiusKm != null && (radiusKm <= 0 || radiusKm > 1000.0 || radiusKm.isNaN() || radiusKm.isInfinite())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Radius must be a positive number up to 1000 km");
        }
        return ResponseEntity.ok(providerProfileService.searchNearby(serviceId, latitude, longitude, radiusKm, type));
    }

    /** Public view of a single verified provider */
    @GetMapping("/api/providers/{id}")
    public ResponseEntity<ProviderDto> getPublic(@PathVariable Long id) {
        return ResponseEntity.ok(providerProfileService.getPublic(id));
    }

    /** Authenticated provider: get own profile */
    @GetMapping("/api/provider/profile")
    public ResponseEntity<ProviderDto> me(Authentication authentication) {
        return ResponseEntity.ok(providerProfileService.me(authentication.getName()));
    }

    /** Authenticated provider: update profile details, services, and areas */
    @PutMapping("/api/provider/profile")
    public ResponseEntity<ProviderDto> update(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody ProviderUpdateRequest request) {
        return ResponseEntity.ok(providerProfileService.update(authentication.getName(), request));
    }

    /** Authenticated provider: update profile photo / logo only */
    @PutMapping("/api/provider/photo")
    public ResponseEntity<ProviderDto> updatePhoto(
            Authentication authentication,
            @RequestBody java.util.Map<String, String> request) {
        String img = request != null ? request.get("profileImage") : null;
        return ResponseEntity.ok(providerProfileService.updatePhoto(authentication.getName(), img));
    }

    /** Authenticated provider: toggle online/offline availability */
    @PutMapping("/api/provider/availability")
    public ResponseEntity<ProviderDto> setAvailability(
            Authentication authentication,
            @RequestBody AvailabilityRequest request) {
        return ResponseEntity.ok(providerProfileService.setAvailability(authentication.getName(), request.available()));
    }
}
