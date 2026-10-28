package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.util.GeoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ProviderProfileService {
    private static final int MAX_AREAS = 10;
    private final ProviderRepository providers;
    private final ProviderAreaRepository areas;
    private final ServiceTypeRepository serviceTypes;
    private final ProviderSubServiceRepository subServicesRepo;
    private final ProviderPortfolioRepository portfolioRepo;
    private final ProviderVideoRepository videosRepo;
    private final BookingRepository bookingRepo;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public ProviderProfileService(ProviderRepository providers,
                                  ProviderAreaRepository areas,
                                  ServiceTypeRepository serviceTypes,
                                  ProviderSubServiceRepository subServicesRepo,
                                  ProviderPortfolioRepository portfolioRepo,
                                  ProviderVideoRepository videosRepo,
                                  BookingRepository bookingRepo,
                                  org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.providers = providers;
        this.areas = areas;
        this.serviceTypes = serviceTypes;
        this.subServicesRepo = subServicesRepo;
        this.portfolioRepo = portfolioRepo;
        this.videosRepo = videosRepo;
        this.bookingRepo = bookingRepo;
        this.eventPublisher = eventPublisher;
    }

    /** Public listing: verified providers only with resilient matching fallback. */
    @Transactional(readOnly = true)
    public List<ProviderDto> search(Long serviceId, String area) {
        return search(serviceId, null, null, null, area);
    }

    @Transactional(readOnly = true)
    public List<ProviderDto> search(Long serviceId, String serviceName, String area) {
        return search(serviceId, serviceName, null, null, area);
    }

    @Transactional(readOnly = true)
    public List<ProviderDto> search(Long serviceId, String serviceName, String subService, Long subServiceId, String area) {
        Long resolvedSid = serviceId;
        if (resolvedSid == null && serviceName != null && !serviceName.isBlank()) {
            resolvedSid = serviceTypes.findByNameIgnoreCase(serviceName.trim())
                    .map(ServiceType::getId)
                    .orElse(0L);
        }
        long sid = resolvedSid == null ? 0L : resolvedSid;
        String cleanArea = area == null ? "" : area.trim().toLowerCase();
        List<Provider> candidates = providers.findCandidates(sid, false);
        candidates = filterCandidatesBySubService(candidates, subService, subServiceId);

        if (!cleanArea.isEmpty()) {
            List<ProviderDto> flex = filterByAreaTokens(candidates, cleanArea);
            if (!flex.isEmpty()) {
                return flex;
            }
        }
        return candidates.stream().map(this::toDto).toList();
    }

    /** Find nearby verified providers within distance and coverage area */
    @Transactional(readOnly = true)
    public List<ProviderDto> searchNearby(Long serviceId, Double lat, Double lon, Double radiusKm, BookingType type) {
        boolean quick = type == BookingType.QUICK_SERVICE;
        long sid = serviceId == null ? 0L : serviceId;
        List<Provider> candidates = providers.findCandidates(sid, quick);

        if (lat == null || lon == null) {
            return candidates.stream().map(this::toDto).toList();
        }
        if (!GeoUtils.isValidCoordinates(lat, lon)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
        }

        List<ProviderDto> results = new ArrayList<>();
        for (Provider p : candidates) {
            if (p.getLatitude() != null && p.getLongitude() != null && GeoUtils.isValidCoordinates(p.getLatitude(), p.getLongitude())) {
                double dist = GeoUtils.distanceKm(lat, lon, p.getLatitude(), p.getLongitude());
                double maxCoverage = p.getServiceRadiusKm() != null ? p.getServiceRadiusKm() : 10.0;
                // Provider must cover customer location
                if (dist <= maxCoverage) {
                    if (radiusKm == null || dist <= radiusKm) {
                        results.add(toDto(p, lat, lon));
                    }
                }
            }
        }

        results.sort(Comparator
                .comparing((ProviderDto d) -> d.distanceKm() != null ? d.distanceKm() : Double.MAX_VALUE)
                .thenComparing(ProviderDto::rating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProviderDto::reviewCount, Comparator.nullsLast(Comparator.reverseOrder())));

        return results;
    }

    /** Matching engine for booking and Quick Service (backward-compatible overload) */
    @Transactional(readOnly = true)
    public List<ProviderDto> match(long serviceId, String area, boolean quick) {
        return match(serviceId, null, null, area, null, null, quick);
    }

    /** Matching engine with optional customer coordinates, resilient area token matching and fallback */
    @Transactional(readOnly = true)
    public List<ProviderDto> match(long serviceId, String area, Double lat, Double lon, boolean quick) {
        return match(serviceId, null, null, area, lat, lon, quick);
    }

    /** Matching engine supporting subservice filtering, coordinates, and area token matching */
    @Transactional(readOnly = true)
    public List<ProviderDto> match(long serviceId, String subService, Long subServiceId, String area, Double lat, Double lon, boolean quick) {
        List<Provider> candidates = providers.findCandidates(serviceId, quick);
        candidates = filterCandidatesBySubService(candidates, subService, subServiceId);
        if (lat != null && lon != null) {
            if (!GeoUtils.isValidCoordinates(lat, lon)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
            }
            List<ProviderDto> matched = new ArrayList<>();
            for (Provider p : candidates) {
                if (p.getLatitude() != null && p.getLongitude() != null && GeoUtils.isValidCoordinates(p.getLatitude(), p.getLongitude())) {
                    double dist = GeoUtils.distanceKm(lat, lon, p.getLatitude(), p.getLongitude());
                    double maxCoverage = p.getServiceRadiusKm() != null ? p.getServiceRadiusKm() : 10.0;
                    if (dist <= maxCoverage) {
                        matched.add(toDto(p, lat, lon));
                    }
                } else if (area != null && !area.isBlank()) {
                    String cleanArea = area.trim().toLowerCase();
                    boolean hasArea = areas.findByProviderId(p.getId()).stream()
                            .anyMatch(a -> matchAreaString(a, cleanArea));
                    if (hasArea) {
                        matched.add(toDto(p));
                    }
                }
            }
            if (!matched.isEmpty()) {
                matched.sort(Comparator
                        .comparing((ProviderDto d) -> d.distanceKm() != null ? d.distanceKm() : Double.MAX_VALUE)
                        .thenComparing(ProviderDto::rating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProviderDto::reviewCount, Comparator.nullsLast(Comparator.reverseOrder())));
                return matched;
            }
        }

        // Area-based query fallback
        String cleanArea = area == null ? "" : area.trim().toLowerCase();
        if (!cleanArea.isEmpty()) {
            List<ProviderDto> matched = filterByAreaTokens(candidates, cleanArea);
            if (!matched.isEmpty()) {
                return matched;
            }
        }

        // Fallback: return candidate providers for this service so customer can book
        return candidates.stream().map(this::toDto).toList();
    }

    private boolean matchAreaString(ProviderArea a, String cleanArea) {
        String areaName = a.getArea() != null ? a.getArea().toLowerCase() : "";
        String cityName = a.getCity() != null ? a.getCity().toLowerCase() : "";
        if (areaName.equals(cleanArea) || cityName.equals(cleanArea)) return true;
        if (!areaName.isEmpty() && (cleanArea.contains(areaName) || areaName.contains(cleanArea))) return true;
        if (!cityName.isEmpty() && (cleanArea.contains(cityName) || cityName.contains(cleanArea))) return true;
        String[] tokens = cleanArea.split("[,\\s]+");
        for (String t : tokens) {
            if (t.length() >= 3 && (areaName.contains(t) || cityName.contains(t))) return true;
        }
        return false;
    }

    private List<ProviderDto> filterByAreaTokens(List<Provider> candidates, String cleanArea) {
        List<ProviderDto> matched = new ArrayList<>();
        for (Provider p : candidates) {
            boolean hasArea = areas.findByProviderId(p.getId()).stream()
                    .anyMatch(a -> matchAreaString(a, cleanArea));
            if (hasArea) {
                matched.add(toDto(p));
            }
        }
        matched.sort(Comparator
                .comparing(ProviderDto::rating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ProviderDto::reviewCount, Comparator.nullsLast(Comparator.reverseOrder())));
        return matched;
    }

    @Transactional(readOnly = true)
    public ProviderDto getPublic(Long id) {
        return providers.findById(id)
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
    }

    @Transactional(readOnly = true)
    public ProviderDto me(String email) {
        return toDto(byEmail(email), null, null, true);
    }

    @Transactional
    public ProviderDto updatePhoto(String email, String img) {
        Provider p = byEmail(email);
        validateAndSetProfileImage(p, img);
        Provider saved = providers.save(p);
        return toDto(saved, null, null, true);
    }

    public void validateAndSetProfileImage(Provider p, String img) {
        if (img == null || img.isBlank()) {
            p.setProfileImage(null);
            return;
        }

        String trimmed = img.trim();

        // 1. HTTP/HTTPS Image URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            if (trimmed.length() > 2048) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL exceeds maximum allowed length (2048 characters)");
            }
            p.setProfileImage(trimmed);
            return;
        }

        // 2. Data URI / Base64 format
        if (trimmed.startsWith("data:image/")) {
            int commaIdx = trimmed.indexOf(',');
            if (commaIdx == -1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed image data URL");
            }

            String metaPart = trimmed.substring(0, commaIdx).toLowerCase();
            String base64Data = trimmed.substring(commaIdx + 1);

            // Validate supported MIME type: JPEG, PNG, WEBP
            boolean isJpeg = metaPart.contains("image/jpeg") || metaPart.contains("image/jpg");
            boolean isPng = metaPart.contains("image/png");
            boolean isWebp = metaPart.contains("image/webp");

            if (!isJpeg && !isPng && !isWebp) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported image format. Supported formats are JPEG, PNG, and WebP.");
            }

            if (!metaPart.contains(";base64")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image data must be Base64-encoded");
            }

            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(base64Data.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Base64 image data");
            }

            // Size validation: 5 MB limit (5 * 1024 * 1024 = 5,242,880 bytes)
            if (decoded.length > 5 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image file size exceeds maximum allowed size (5 MB)");
            }

            if (decoded.length < 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file data is too small to be a valid image");
            }

            // Magic bytes verification
            boolean validMagic = false;
            if (isJpeg) {
                // JPEG magic bytes: FF D8 FF
                validMagic = (decoded[0] == (byte) 0xFF && decoded[1] == (byte) 0xD8 && decoded[2] == (byte) 0xFF);
            } else if (isPng) {
                // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
                validMagic = (decoded[0] == (byte) 0x89 && decoded[1] == (byte) 0x50 && decoded[2] == (byte) 0x4E && decoded[3] == (byte) 0x47);
            } else if (isWebp) {
                // WebP magic bytes: RIFF....WEBP
                validMagic = (decoded[0] == 'R' && decoded[1] == 'I' && decoded[2] == 'F' && decoded[3] == 'F' &&
                              decoded[8] == 'W' && decoded[9] == 'E' && decoded[10] == 'B' && decoded[11] == 'P');
            }

            if (!validMagic) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "File content does not match the declared image format (JPEG, PNG, or WebP).");
            }

            p.setProfileImage(trimmed);
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid image format. Provide a valid HTTP/HTTPS URL or a Base64 data URL (JPEG, PNG, WebP).");
    }

    @Transactional
    public ProviderDto update(String email, ProviderUpdateRequest r) {
        Provider p = byEmail(email);

        if (r.name() != null && !r.name().isBlank() && p.getUser() != null) {
            p.getUser().setName(r.name().trim());
        }
        if (r.phone() != null && p.getUser() != null) {
            p.getUser().setPhone(r.phone().trim());
        }

        if (r.approxPrice() != null && r.approxPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approximate price cannot be negative");
        }

        Set<Long> ids = new HashSet<>(r.serviceIds() != null ? r.serviceIds() : List.of());
        List<ServiceType> found = serviceTypes.findAllById(ids);
        if (found.size() != ids.size() || found.stream().anyMatch(s -> s.getStatus() != ServiceStatus.ACTIVE))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected services are invalid");
        if (r.areas() != null && r.areas().size() > MAX_AREAS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can add at most " + MAX_AREAS + " areas");

        if (r.businessName() != null && !r.businessName().isBlank()) {
            p.setBusinessName(r.businessName().trim());
        }
        if (r.description() != null) p.setDescription(r.description());
        if (r.experienceYears() != null) p.setExperienceYears(Math.max(0, r.experienceYears()));
        if (r.approxPrice() != null) p.setApproxPrice(r.approxPrice());
        p.setServices(new HashSet<>(found));

        if (r.formattedAddress() != null) p.setFormattedAddress(r.formattedAddress().trim());
        if (r.latitude() != null || r.longitude() != null) {
            if (!GeoUtils.isValidCoordinates(r.latitude(), r.longitude())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180");
            }
            p.setLatitude(r.latitude());
            p.setLongitude(r.longitude());
        }
        if (r.serviceRadiusKm() != null && r.serviceRadiusKm() > 0) p.setServiceRadiusKm(r.serviceRadiusKm());
        if (r.workingHours() != null && !r.workingHours().isBlank()) p.setWorkingHours(r.workingHours().trim());
        if (r.profileImage() != null) {
            validateAndSetProfileImage(p, r.profileImage());
        }

        areas.deleteByProviderId(p.getId());
        Set<String> seen = new HashSet<>();
        if (r.areas() != null) {
            for (AreaDto a : r.areas()) {
                String area = a.area() != null ? a.area().trim() : "";
                String city = a.city() != null ? a.city().trim() : "";
                if (area.isEmpty() || city.isEmpty()) continue;
                if (!seen.add((area + "|" + city).toLowerCase())) continue; // skip duplicates
                ProviderArea pa = new ProviderArea();
                pa.setProviderId(p.getId());
                pa.setArea(area);
                pa.setCity(city);
                areas.save(pa);
            }
        }

        if (r.subServices() != null) {
            subServicesRepo.deleteByProviderId(p.getId());
            for (ProviderSubServiceDto sDto : r.subServices()) {
                if (sDto.name() == null || sDto.name().isBlank()) continue;
                if (sDto.price() != null && sDto.price().compareTo(BigDecimal.ZERO) < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sub-service price cannot be negative");
                }
                ProviderSubService s = new ProviderSubService();
                s.setProviderId(p.getId());
                s.setName(sDto.name().trim());
                s.setCategory(sDto.category() != null ? sDto.category().trim() : getPrimaryCategory(p));
                s.setDescription(sDto.description());
                s.setPrice(sDto.price() != null ? sDto.price() : p.getApproxPrice());
                s.setDuration(sDto.duration() != null ? sDto.duration() : "45 mins");
                s.setImageUrl(sDto.imageUrl());
                s.setEnabled(sDto.enabled());
                subServicesRepo.save(s);
            }
        }

        if (r.portfolioItems() != null) {
            portfolioRepo.deleteByProviderId(p.getId());
            for (ProviderPortfolioDto pfDto : r.portfolioItems()) {
                if (pfDto.title() == null || pfDto.title().isBlank()) continue;
                ProviderPortfolio pf = new ProviderPortfolio();
                pf.setProviderId(p.getId());
                pf.setTitle(pfDto.title().trim());
                pf.setSubService(pfDto.subService() != null ? pfDto.subService().trim() : getPrimaryCategory(p));
                pf.setBeforeImageUrl(pfDto.beforeImageUrl());
                pf.setAfterImageUrl(pfDto.afterImageUrl());
                pf.setDescription(pfDto.description());
                pf.setCompletedDate(pfDto.completedDate());
                portfolioRepo.save(pf);
            }
        }

        if (r.workVideos() != null) {
            videosRepo.deleteByProviderId(p.getId());
            for (ProviderVideoDto vDto : r.workVideos()) {
                if (vDto.title() == null || vDto.title().isBlank()) continue;
                ProviderVideo v = new ProviderVideo();
                v.setProviderId(p.getId());
                v.setTitle(vDto.title().trim());
                v.setSubService(vDto.subService() != null ? vDto.subService().trim() : getPrimaryCategory(p));
                v.setVideoUrl(vDto.videoUrl());
                v.setThumbnailUrl(vDto.thumbnailUrl());
                v.setDuration(vDto.duration() != null ? vDto.duration() : "0:45");
                v.setDescription(vDto.description());
                videosRepo.save(v);
            }
        }

        // A provider with no services or location/areas can't be matched, so take them offline
        if (found.isEmpty() || (seen.isEmpty() && p.getLatitude() == null)) p.setAvailability(false);
        return toDto(providers.save(p), null, null, true);
    }

    @Transactional
    public ProviderDto setAvailability(String email, boolean on) {
        Provider p = byEmail(email);
        boolean wasOnline = p.isAvailability();
        if (on) {
            if (p.getVerificationStatus() != VerificationStatus.VERIFIED)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An admin must verify your account before you can go online");
            if (p.getServices().isEmpty() || (areas.findByProviderId(p.getId()).isEmpty() && p.getLatitude() == null))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add at least one service and one service area or location first");
        }
        p.setAvailability(on);
        Provider saved = providers.save(p);
        if (on && !wasOnline && eventPublisher != null) {
            eventPublisher.publishEvent(new ProviderAvailableEvent(saved));
        }
        return toDto(saved, null, null, true);
    }

    private Provider byEmail(String email) {
        return providers.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider profile not found"));
    }

    public ProviderDto toDto(Provider p) {
        return toDto(p, null, null, false);
    }

    public ProviderDto toDto(Provider p, Double customerLat, Double customerLon) {
        return toDto(p, customerLat, customerLon, false);
    }

    public ProviderDto toDto(Provider p, Double customerLat, Double customerLon, boolean isOwner) {
        Double distanceKm = null;
        if (customerLat != null && customerLon != null && p.getLatitude() != null && p.getLongitude() != null) {
            distanceKm = GeoUtils.distanceKm(customerLat, customerLon, p.getLatitude(), p.getLongitude());
        }

        List<ProviderSubServiceDto> subServiceDtos = subServicesRepo.findByProviderId(p.getId()).stream()
                .map(s -> new ProviderSubServiceDto(s.getId(), s.getProviderId(), s.getName(), s.getCategory(),
                        s.getDescription(), s.getPrice(), s.getDuration(), s.getImageUrl(), s.isEnabled()))
                .toList();
        if (subServiceDtos.isEmpty()) {
            subServiceDtos = generateDefaultSubServices(p);
        }

        List<ProviderPortfolioDto> portfolioDtos = portfolioRepo.findByProviderIdOrderByIdAsc(p.getId()).stream()
                .map(pf -> new ProviderPortfolioDto(pf.getId(), pf.getProviderId(), pf.getTitle(), pf.getSubService(),
                        pf.getBeforeImageUrl(), pf.getAfterImageUrl(), pf.getDescription(), pf.getCompletedDate()))
                .toList();
        if (portfolioDtos.isEmpty()) {
            portfolioDtos = generateDefaultPortfolio(p);
        }

        List<ProviderVideoDto> videoDtos = videosRepo.findByProviderIdOrderByIdAsc(p.getId()).stream()
                .map(v -> new ProviderVideoDto(v.getId(), v.getProviderId(), v.getTitle(), v.getSubService(),
                        v.getVideoUrl(), v.getThumbnailUrl(), v.getDuration(), v.getDescription()))
                .toList();
        if (videoDtos.isEmpty()) {
            videoDtos = generateDefaultVideos(p);
        }

        int completedCount = (int) bookingRepo.countByProviderIdAndStatus(p.getId(), BookingStatus.COMPLETED);
        if (completedCount == 0 && p.getExperienceYears() != null && p.getExperienceYears() > 0) {
            completedCount = p.getExperienceYears() * 15 + 18;
        }

        String providerName = null;
        String providerEmail = null;
        String providerPhone = null;

        if (p.getUser() != null) {
            providerName = p.getUser().getName();
            if (isOwner) {
                providerEmail = p.getUser().getEmail();
                providerPhone = p.getUser().getPhone();
            }
        }

        return new ProviderDto(
                p.getId(),
                p.getBusinessName(),
                p.getDescription(),
                p.getExperienceYears(),
                p.getApproxPrice(),
                p.getVerificationStatus().name(),
                p.isAvailability(),
                p.getRating(),
                p.getReviewCount(),
                p.getServices().stream().sorted(Comparator.comparing(ServiceType::getName)).map(CatalogService::toDto).toList(),
                areas.findByProviderId(p.getId()).stream().map(a -> new AreaDto(a.getArea(), a.getCity())).toList(),
                p.getFormattedAddress(),
                p.getLatitude(),
                p.getLongitude(),
                p.getServiceRadiusKm(),
                distanceKm,
                p.getProfileImage(),
                p.getWorkingHours() != null ? p.getWorkingHours() : "Mon - Sun: 8:00 AM - 8:00 PM",
                completedCount,
                subServiceDtos,
                portfolioDtos,
                videoDtos,
                providerName,
                providerEmail,
                providerPhone,
                p.isDemo()
        );
    }

    private String getPrimaryCategory(Provider p) {
        if (p.getServices() != null && !p.getServices().isEmpty()) {
            return p.getServices().iterator().next().getName();
        }
        return "General";
    }

    private List<ProviderSubServiceDto> generateDefaultSubServices(Provider p) {
        String cat = getPrimaryCategory(p).toLowerCase();
        List<ProviderSubServiceDto> list = new ArrayList<>();
        long pid = p.getId();

        if (cat.contains("clean")) {
            list.add(new ProviderSubServiceDto(1L, pid, "Sofa Cleaning", "Cleaner", "Deep vacuuming, wet shampooing, and anti-allergen extraction.", new java.math.BigDecimal("499.00"), "40 mins", "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Carpet Deep Cleaning", "Cleaner", "High-suction vacuuming and stain extraction for wool and synthetic carpets.", new java.math.BigDecimal("399.00"), "35 mins", "https://images.unsplash.com/photo-1600121848594-d8644e57abab?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(3L, pid, "Kitchen Deep Degreasing", "Cleaner", "Heavy oil and carbon removal from stovetop, chimney, tiles and cabinets.", new java.math.BigDecimal("599.00"), "60 mins", "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(4L, pid, "Bathroom Sanitization & Descaling", "Cleaner", "Hard water stain removal, tile scaling, and germicidal sanitization.", new java.math.BigDecimal("349.00"), "30 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(5L, pid, "Mattress Deep Sanitization", "Cleaner", "Dry extraction and anti-dust-mite treatment for double bed mattress.", new java.math.BigDecimal("449.00"), "45 mins", "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(6L, pid, "Full Home Deep Clean", "Cleaner", "Top-to-bottom scrub, appliance wipe down, window glass and floor buffing.", new java.math.BigDecimal("1499.00"), "180 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=400&q=80", true));
        } else if (cat.contains("electr")) {
            list.add(new ProviderSubServiceDto(1L, pid, "Ceiling & Exhaust Fan Installation", "Electrician", "Mounting, blade balancing, downrod setup, and regulator wiring.", new java.math.BigDecimal("199.00"), "30 mins", "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Switch, Socket & Dimmer Replacement", "Electrician", "Shock-proof modular gang box installation and continuity testing.", new java.math.BigDecimal("149.00"), "20 mins", "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(3L, pid, "MCB Fuse Box & Tripping Repair", "Electrician", "Short circuit diagnosis, phase balancing, and DIN rail breaker replacement.", new java.math.BigDecimal("349.00"), "45 mins", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(4L, pid, "Inverter Wiring & Battery Setup", "Electrician", "Bypass changeover switch, inverter rack setup, and acid level test.", new java.math.BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1618221195710-dd6b41faaea6?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(5L, pid, "Chandelier & LED Cove Lighting", "Electrician", "Concealed profile lighting, ceiling driver installation, and warm LED strip setup.", new java.math.BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=400&q=80", true));
        } else if (cat.contains("plumb")) {
            list.add(new ProviderSubServiceDto(1L, pid, "Tap & Shower Mixer Fitting", "Plumber", "Installation of dual hot/cold mixer, angle valve, or basin tap.", new java.math.BigDecimal("199.00"), "25 mins", "https://images.unsplash.com/photo-1584622781564-1d987f7333c1?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Drain Blockage & Pipeline De-clog", "Plumber", "Heavy motorized clearing of choked kitchen trap, sink pipe, or shower drain.", new java.math.BigDecimal("299.00"), "35 mins", "https://images.unsplash.com/photo-1585704032915-c3400ca199e7?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(3L, pid, "Toilet Flush Tank & Syphon Repair", "Plumber", "Fixing continuous overflow, inlet float valve, and cistern leakage.", new java.math.BigDecimal("349.00"), "40 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(4L, pid, "Water Motor & Pump Fitting", "Plumber", "Water pump piping, foot valve check, and airtight priming.", new java.math.BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=400&q=80", true));
        } else if (cat.contains("ac") || cat.contains("fridge") || cat.contains("appliance")) {
            list.add(new ProviderSubServiceDto(1L, pid, "Foam Jet AC Deep Service", "AC/Fridge Repair", "Indoor coil power wash with foam jet jacket to boost cooling performance.", new java.math.BigDecimal("599.00"), "45 mins", "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "AC Gas Refill & Leak Testing", "AC/Fridge Repair", "Nitrogen pressure test, flare nut brazing, and pure R32/R410A refill.", new java.math.BigDecimal("1899.00"), "60 mins", "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(3L, pid, "Refrigerator Cooling & Compressor Check", "AC/Fridge Repair", "Thermostat relay diagnosis, defrost heater check, and gas top-up.", new java.math.BigDecimal("399.00"), "40 mins", "https://images.unsplash.com/photo-1584992236310-6edddc08acff?auto=format&fit=crop&w=400&q=80", true));
        } else if (cat.contains("barber")) {
            list.add(new ProviderSubServiceDto(1L, pid, "Men's Haircut & Head Massage", "Barber", "Custom fade cut, styling, and 10-minute relaxing scalp acupressure massage.", new java.math.BigDecimal("249.00"), "35 mins", "https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Beard Styling & Hot Towel Steam", "Barber", "Razor line shaping, nourishing beard oil, and hot towel facial steam.", new java.math.BigDecimal("199.00"), "25 mins", "https://images.unsplash.com/photo-1622286342621-4bd786c2447c?auto=format&fit=crop&w=400&q=80", true));
        } else if (cat.contains("mechanic")) {
            list.add(new ProviderSubServiceDto(1L, pid, "General Bike Doorstep Service", "Mechanic", "Engine oil replacement, brake shoe adjustment, chain lube, and spark plug cleaning.", new java.math.BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Emergency Battery Jumpstart & Puncture", "Mechanic", "On-spot 12V booster cable jumpstart and tubeless puncture plugging.", new java.math.BigDecimal("249.00"), "20 mins", "https://images.unsplash.com/photo-1619642751034-765dfdf7c58e?auto=format&fit=crop&w=400&q=80", true));
        } else {
            java.math.BigDecimal base = p.getApproxPrice() != null ? p.getApproxPrice() : new java.math.BigDecimal("299.00");
            list.add(new ProviderSubServiceDto(1L, pid, "Standard Inspection & Diagnosis", p.getBusinessName(), "On-site safety audit, troubleshooting, and transparent estimate.", base, "30 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=400&q=80", true));
            list.add(new ProviderSubServiceDto(2L, pid, "Comprehensive Doorstep Service", p.getBusinessName(), "Full doorstep service performed by certified specialist.", base.add(new java.math.BigDecimal("150.00")), "60 mins", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=400&q=80", true));
        }
        return list;
    }

    private List<ProviderPortfolioDto> generateDefaultPortfolio(Provider p) {
        String cat = getPrimaryCategory(p).toLowerCase();
        List<ProviderPortfolioDto> list = new ArrayList<>();
        long pid = p.getId();

        if (cat.contains("clean")) {
            list.add(new ProviderPortfolioDto(1L, pid, "3-Seater Velvet Sofa Deep Shampooing", "Sofa Cleaning",
                    "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=800&q=80",
                    "Removed stubborn tea and food stains using specialized extraction machine.", "October 2026"));
            list.add(new ProviderPortfolioDto(2L, pid, "Kitchen Backsplash & Stovetop Degreasing", "Kitchen Deep Degreasing",
                    "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1556912172-45b7abe8b7e1?auto=format&fit=crop&w=800&q=80",
                    "Tough grease deposits and carbon residue completely eliminated from cooking area.", "September 2026"));
            list.add(new ProviderPortfolioDto(3L, pid, "Living Room Area Rug Stain Removal", "Carpet Deep Cleaning",
                    "https://images.unsplash.com/photo-1600121848594-d8644e57abab?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=800&q=80",
                    "High-suction wet extraction restored natural fiber color and softness.", "September 2026"));
        } else if (cat.contains("electr")) {
            list.add(new ProviderPortfolioDto(1L, pid, "Main Distribution Box & MCB Wire Dressing", "MCB Fuse Box",
                    "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=800&q=80",
                    "Transformed dangerous tangled cables into an organized, safety-compliant DIN-rail breaker board.", "October 2026"));
            list.add(new ProviderPortfolioDto(2L, pid, "Modular Plate & Switch Fitting", "Switch & Socket",
                    "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=800&q=80",
                    "Replaced burnt old switches with fire-retardant surge-protected modular switches.", "September 2026"));
        } else if (cat.contains("plumb")) {
            list.add(new ProviderPortfolioDto(1L, pid, "Corroded Wall Mixer to Luxury Chrome Fitting", "Tap & Shower Mixer",
                    "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1584622781564-1d987f7333c1?auto=format&fit=crop&w=800&q=80",
                    "Replaced leaking corroded valve with brass chrome dual-flow mixer.", "October 2026"));
            list.add(new ProviderPortfolioDto(2L, pid, "Severe Under-Sink Trap Blockage Clearance", "Drain Blockage",
                    "https://images.unsplash.com/photo-1585704032915-c3400ca199e7?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=800&q=80",
                    "Restored rapid drainage flow and installed clean anti-clog mesh trap.", "September 2026"));
        } else {
            list.add(new ProviderPortfolioDto(1L, pid, "Standard Service Overhaul", p.getBusinessName(),
                    "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=800&q=80",
                    "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=800&q=80",
                    "Complete inspection, parts renewal, and testing performed to factory standards.", "October 2026"));
        }
        return list;
    }

    private List<ProviderVideoDto> generateDefaultVideos(Provider p) {
        String cat = getPrimaryCategory(p).toLowerCase();
        List<ProviderVideoDto> list = new ArrayList<>();
        long pid = p.getId();

        if (cat.contains("clean")) {
            list.add(new ProviderVideoDto(1L, pid, "Live Foam Jet Sofa Cleaning Demo", "Sofa Cleaning",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?auto=format&fit=crop&w=600&q=80",
                    "0:45", "Demonstration of deep extraction foam shampooing removing embedded dirt."));
            list.add(new ProviderVideoDto(2L, pid, "Kitchen Degreasing High-Pressure Steam", "Kitchen Deep Degreasing",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=600&q=80",
                    "0:38", "Watch pressurized steam dissolve thick grease from kitchen chimneys."));
        } else if (cat.contains("electr")) {
            list.add(new ProviderVideoDto(1L, pid, "MCB Panel Tripping Diagnostic Demo", "MCB Fuse Box",
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80",
                    "0:42", "Testing phase leakage and replacing faulty breakers in live distribution board."));
        } else {
            list.add(new ProviderVideoDto(1L, pid, "Specialist Work In Action", p.getBusinessName(),
                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyBlazes.mp4",
                    "https://images.unsplash.com/photo-1584622781564-1d987f7333c1?auto=format&fit=crop&w=600&q=80",
                    "0:35", "Professional precision execution and clean finish at customer doorstep."));
        }
        return list;
    }

    private List<Provider> filterCandidatesBySubService(List<Provider> list, String subName, Long subId) {
        if ((subName == null || subName.isBlank()) && subId == null) {
            return list;
        }
        String cleanSub = subName != null ? subName.trim().toLowerCase() : "";
        List<Provider> filtered = new ArrayList<>();
        for (Provider p : list) {
            var subs = subServicesRepo.findByProviderId(p.getId());
            boolean matches = subs.stream().anyMatch(s -> {
                if (subId != null && s.getId().equals(subId)) return true;
                if (!cleanSub.isEmpty()) {
                    String n = s.getName() != null ? s.getName().toLowerCase() : "";
                    String c = s.getCategory() != null ? s.getCategory().toLowerCase() : "";
                    String d = s.getDescription() != null ? s.getDescription().toLowerCase() : "";
                    return n.contains(cleanSub) || cleanSub.contains(n) || c.contains(cleanSub) || d.contains(cleanSub);
                }
                return false;
            });
            if (matches) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    @Transactional
    public void deleteProviderArtifacts(Long providerId) {
        areas.deleteByProviderId(providerId);
        subServicesRepo.deleteByProviderId(providerId);
        portfolioRepo.deleteByProviderId(providerId);
        videosRepo.deleteByProviderId(providerId);
    }
}
