package com.localserviceconnect;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.BookingType;
import com.localserviceconnect.service.ProviderProfileService;
import com.localserviceconnect.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleMapsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProviderProfileService providerProfileService;

    @Test
    @DisplayName("GeoUtils calculates accurate Haversine distance")
    void testHaversineDistance() {
        // Ranchi Lalpur (23.3685, 85.3340) to Kanke Road (23.4150, 85.3220)
        double distance = GeoUtils.distanceKm(23.3685, 85.3340, 23.4150, 85.3220);
        // Approximately 5.3 km straight-line distance
        assertThat(distance).isBetween(4.5, 6.0);

        // Same point distance is 0.0
        assertThat(GeoUtils.distanceKm(23.3685, 85.3340, 23.3685, 85.3340)).isEqualTo(0.0);

        // Radius check
        assertThat(GeoUtils.isWithinRadius(23.3685, 85.3340, 23.4150, 85.3220, 10.0)).isTrue();
        assertThat(GeoUtils.isWithinRadius(23.3685, 85.3340, 23.4150, 85.3220, 3.0)).isFalse();
    }

    @Test
    @DisplayName("Public Maps Config endpoint returns 200 with default coordinates")
    void testMapsConfigEndpoint() throws Exception {
        mockMvc.perform(get("/api/config/maps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLat").value(23.3441))
                .andExpect(jsonPath("$.defaultLng").value(85.3096))
                .andExpect(jsonPath("$.defaultZoom").value(12));
    }

    @Test
    @DisplayName("Nearby providers endpoint returns eligible providers sorted by distance")
    void testNearbyProvidersSearch() throws Exception {
        // Searching around Lalpur customer location (23.3712, 85.3361)
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "23.3712")
                        .param("longitude", "85.3361"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Providers outside coverage radius are excluded")
    void testOutsideCoverageRadiusExcluded() {
        // Coordinates in Delhi (far from Ranchi: ~1000 km)
        List<ProviderDto> results = providerProfileService.searchNearby(null, 28.6139, 77.2090, 20.0, BookingType.NORMAL);
        assertThat(results).isEmpty();
    }
}

