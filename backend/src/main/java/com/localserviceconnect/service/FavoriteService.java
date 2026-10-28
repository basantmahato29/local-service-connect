package com.localserviceconnect.service;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.CustomerRepository;
import com.localserviceconnect.repository.FavoriteRepository;
import com.localserviceconnect.repository.NotificationRepository;
import com.localserviceconnect.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final CustomerRepository customerRepository;
    private final ProviderRepository providerRepository;
    private final ProviderProfileService providerProfileService;
    private final NotificationRepository notificationRepository;

    public FavoriteService(
            FavoriteRepository favoriteRepository,
            CustomerRepository customerRepository,
            ProviderRepository providerRepository,
            ProviderProfileService providerProfileService,
            NotificationRepository notificationRepository
    ) {
        this.favoriteRepository = favoriteRepository;
        this.customerRepository = customerRepository;
        this.providerRepository = providerRepository;
        this.providerProfileService = providerProfileService;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Toggles favorite status for the authenticated customer.
     * If already favorited, removes it. If not, adds it.
     */
    @Transactional
    public ToggleFavoriteResponse toggleFavorite(String customerEmail, Long providerId) {
        Customer customer = getCustomerByEmail(customerEmail);
        Provider provider = getEligibleProvider(providerId);

        Optional<Favorite> existing = favoriteRepository.findByCustomerIdAndProviderId(customer.getId(), provider.getId());
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            log.info("Customer {} removed provider {} from favorites", customer.getId(), provider.getId());
            return new ToggleFavoriteResponse(provider.getId(), false, "Provider removed from favorites");
        } else {
            Favorite favorite = new Favorite(customer, provider);
            favoriteRepository.save(favorite);
            log.info("Customer {} added provider {} to favorites", customer.getId(), provider.getId());
            return new ToggleFavoriteResponse(provider.getId(), true, "Provider added to favorites");
        }
    }

    /**
     * Idempotent add to favorites.
     */
    @Transactional
    public ToggleFavoriteResponse addFavorite(String customerEmail, Long providerId) {
        Customer customer = getCustomerByEmail(customerEmail);
        Provider provider = getEligibleProvider(providerId);

        Optional<Favorite> existing = favoriteRepository.findByCustomerIdAndProviderId(customer.getId(), provider.getId());
        if (existing.isPresent()) {
            return new ToggleFavoriteResponse(provider.getId(), true, "Provider is already in your favorites");
        }

        Favorite favorite = new Favorite(customer, provider);
        favoriteRepository.save(favorite);
        log.info("Customer {} added provider {} to favorites", customer.getId(), provider.getId());
        return new ToggleFavoriteResponse(provider.getId(), true, "Provider added to favorites");
    }

    /**
     * Idempotent remove from favorites.
     */
    @Transactional
    public ToggleFavoriteResponse removeFavorite(String customerEmail, Long providerId) {
        Customer customer = getCustomerByEmail(customerEmail);
        if (providerId == null || providerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid provider ID is required");
        }

        Optional<Favorite> existing = favoriteRepository.findByCustomerIdAndProviderId(customer.getId(), providerId);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            log.info("Customer {} removed provider {} from favorites", customer.getId(), providerId);
        }
        return new ToggleFavoriteResponse(providerId, false, "Provider removed from favorites");
    }

    /**
     * Retrieves all favorites for the authenticated customer with full provider details.
     */
    @Transactional(readOnly = true)
    public List<FavoriteDto> getCustomerFavorites(String customerEmail) {
        Customer customer = getCustomerByEmail(customerEmail);
        List<Favorite> favorites = favoriteRepository.findByCustomerIdWithProvider(customer.getId());

        List<FavoriteDto> dtos = new ArrayList<>();
        Double customerLat = customer.getLatitude();
        Double customerLon = customer.getLongitude();

        for (Favorite fav : favorites) {
            Provider p = fav.getProvider();
            // Only include providers who are currently verified and active
            if (p.getVerificationStatus() == VerificationStatus.VERIFIED && (p.getUser() == null || p.getUser().isEnabled())) {
                ProviderDto pDto = providerProfileService.toDto(p, customerLat, customerLon, false);
                dtos.add(new FavoriteDto(
                        fav.getId(),
                        p.getId(),
                        pDto,
                        fav.isNotifyOnAvailable(),
                        fav.getCreatedAt()
                ));
            }
        }
        return dtos;
    }

    /**
     * Retrieves high-performance list of provider IDs favorited by the customer to avoid N+1 queries on listing pages.
     */
    @Transactional(readOnly = true)
    public FavoriteIdsResponse getCustomerFavoriteIds(String customerEmail) {
        Customer customer = getCustomerByEmail(customerEmail);
        List<Long> ids = favoriteRepository.findProviderIdsByCustomerId(customer.getId());
        return new FavoriteIdsResponse(ids != null ? ids : List.of());
    }

    /**
     * Checks favorite status for a single provider.
     */
    @Transactional(readOnly = true)
    public FavoriteStatusDto getFavoriteStatus(String customerEmail, Long providerId) {
        Customer customer = getCustomerByEmail(customerEmail);
        if (providerId == null || providerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid provider ID is required");
        }

        Optional<Favorite> fav = favoriteRepository.findByCustomerIdAndProviderId(customer.getId(), providerId);
        return new FavoriteStatusDto(
                providerId,
                fav.isPresent(),
                fav.map(Favorite::isNotifyOnAvailable).orElse(false)
        );
    }

    /**
     * Updates notification preference for availability of a favorited provider.
     */
    @Transactional
    public FavoriteStatusDto updateNotificationPreference(String customerEmail, Long providerId, boolean notifyOnAvailable) {
        Customer customer = getCustomerByEmail(customerEmail);
        Provider provider = getEligibleProvider(providerId);

        Favorite fav = favoriteRepository.findByCustomerIdAndProviderId(customer.getId(), provider.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider is not in your favorites"));

        fav.setNotifyOnAvailable(notifyOnAvailable);
        favoriteRepository.save(fav);
        log.info("Customer {} set notifyOnAvailable={} for provider {}", customer.getId(), notifyOnAvailable, provider.getId());
        return new FavoriteStatusDto(provider.getId(), true, notifyOnAvailable);
    }

    @org.springframework.context.event.EventListener
    public void onProviderAvailable(ProviderAvailableEvent event) {
        if (event != null && event.provider() != null) {
            notifyCustomersOnProviderAvailable(event.provider());
        }
    }

    /**
     * Helper to broadcast availability notification when a provider goes online.
     */
    @Transactional
    public void notifyCustomersOnProviderAvailable(Provider provider) {
        if (provider == null || provider.getId() == null) return;
        List<Favorite> favorites = favoriteRepository.findByProviderIdAndNotifyOnAvailableTrue(provider.getId());
        if (favorites.isEmpty()) return;

        String providerName = provider.getBusinessName();
        for (Favorite fav : favorites) {
            Customer cust = fav.getCustomer();
            if (cust == null || cust.getUser() == null) continue;

            User custUser = cust.getUser();
            Notification n = new Notification();
            n.setRecipient(custUser);
            n.setTitle("Favorite Provider is Now Online! 🌟");
            n.setMessage(providerName + " is now online and available for bookings in your area. Book now to get priority doorstep service!");
            n.setType("PROVIDER_AVAILABLE");
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
        log.info("Sent availability notifications to {} customers for provider {}", favorites.size(), provider.getId());
    }

    private Customer getCustomerByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer profile not found"));
    }

    private Provider getEligibleProvider(Long providerId) {
        if (providerId == null || providerId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid provider ID is required");
        }

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));

        if (provider.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only verified providers can be added to favorites");
        }

        if (provider.getUser() != null && !provider.getUser().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This provider account is currently inactive");
        }

        return provider;
    }
}
