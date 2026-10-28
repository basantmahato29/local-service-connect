package com.localserviceconnect.controller;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * Toggles or adds a provider to favorites for the authenticated customer.
     */
    @PostMapping("/{providerId}")
    public ResponseEntity<ToggleFavoriteResponse> toggleFavorite(
            Authentication authentication,
            @PathVariable Long providerId
    ) {
        return ResponseEntity.ok(favoriteService.toggleFavorite(authentication.getName(), providerId));
    }

    /**
     * Removes a provider from favorites for the authenticated customer.
     */
    @DeleteMapping("/{providerId}")
    public ResponseEntity<ToggleFavoriteResponse> removeFavorite(
            Authentication authentication,
            @PathVariable Long providerId
    ) {
        return ResponseEntity.ok(favoriteService.removeFavorite(authentication.getName(), providerId));
    }

    /**
     * Retrieves all favorite providers for the authenticated customer.
     */
    @GetMapping
    public ResponseEntity<List<FavoriteDto>> getFavorites(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.getCustomerFavorites(authentication.getName()));
    }

    /**
     * Retrieves batch array of favorited provider IDs for efficient client-side card status rendering.
     */
    @GetMapping("/ids")
    public ResponseEntity<FavoriteIdsResponse> getFavoriteIds(Authentication authentication) {
        return ResponseEntity.ok(favoriteService.getCustomerFavoriteIds(authentication.getName()));
    }

    /**
     * Checks favorite status for a single provider.
     */
    @GetMapping("/{providerId}/status")
    public ResponseEntity<FavoriteStatusDto> getStatus(
            Authentication authentication,
            @PathVariable Long providerId
    ) {
        return ResponseEntity.ok(favoriteService.getFavoriteStatus(authentication.getName(), providerId));
    }

    /**
     * Updates availability notification preference for a favorited provider.
     */
    @PutMapping("/{providerId}/notify")
    public ResponseEntity<FavoriteStatusDto> updateNotification(
            Authentication authentication,
            @PathVariable Long providerId,
            @RequestBody UpdateFavoriteNotifyRequest request
    ) {
        boolean notify = request != null && request.notifyOnAvailable();
        return ResponseEntity.ok(favoriteService.updateNotificationPreference(authentication.getName(), providerId, notify));
    }
}
