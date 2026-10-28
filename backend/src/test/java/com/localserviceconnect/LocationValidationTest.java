package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import com.localserviceconnect.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LocationValidationTest {

    @Autowired
    private MockMvc mockMvc;

    // ==========================================
    // 1. UNIT TESTS: GeoUtils Bounds & Defense
    // ==========================================

    @Test
    @DisplayName("Unit: GeoUtils validates valid latitudes within [-90.0, 90.0]")
    void testValidLatitudes() {
        assertThat(GeoUtils.isValidLatitude(0.0)).isTrue();
        assertThat(GeoUtils.isValidLatitude(23.3441)).isTrue();
        assertThat(GeoUtils.isValidLatitude(-45.5)).isTrue();
        assertThat(GeoUtils.isValidLatitude(90.0)).isTrue();
        assertThat(GeoUtils.isValidLatitude(-90.0)).isTrue();
        assertThat(GeoUtils.isValidLatitude(89.999999)).isTrue();
        assertThat(GeoUtils.isValidLatitude(-89.999999)).isTrue();
    }

    @Test
    @DisplayName("Unit: GeoUtils rejects out-of-bound or special-valued latitudes")
    void testInvalidLatitudes() {
        assertThat(GeoUtils.isValidLatitude(90.000001)).isFalse();
        assertThat(GeoUtils.isValidLatitude(-90.000001)).isFalse();
        assertThat(GeoUtils.isValidLatitude(100.0)).isFalse();
        assertThat(GeoUtils.isValidLatitude(-180.0)).isFalse();
        assertThat(GeoUtils.isValidLatitude(Double.NaN)).isFalse();
        assertThat(GeoUtils.isValidLatitude(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(GeoUtils.isValidLatitude(Double.NEGATIVE_INFINITY)).isFalse();
        assertThat(GeoUtils.isValidLatitude(null)).isFalse();
    }

    @Test
    @DisplayName("Unit: GeoUtils validates valid longitudes within [-180.0, 180.0]")
    void testValidLongitudes() {
        assertThat(GeoUtils.isValidLongitude(0.0)).isTrue();
        assertThat(GeoUtils.isValidLongitude(85.3096)).isTrue();
        assertThat(GeoUtils.isValidLongitude(-122.4194)).isTrue();
        assertThat(GeoUtils.isValidLongitude(180.0)).isTrue();
        assertThat(GeoUtils.isValidLongitude(-180.0)).isTrue();
        assertThat(GeoUtils.isValidLongitude(179.999999)).isTrue();
        assertThat(GeoUtils.isValidLongitude(-179.999999)).isTrue();
    }

    @Test
    @DisplayName("Unit: GeoUtils rejects out-of-bound or special-valued longitudes")
    void testInvalidLongitudes() {
        assertThat(GeoUtils.isValidLongitude(180.000001)).isFalse();
        assertThat(GeoUtils.isValidLongitude(-180.000001)).isFalse();
        assertThat(GeoUtils.isValidLongitude(200.0)).isFalse();
        assertThat(GeoUtils.isValidLongitude(-360.0)).isFalse();
        assertThat(GeoUtils.isValidLongitude(Double.NaN)).isFalse();
        assertThat(GeoUtils.isValidLongitude(Double.POSITIVE_INFINITY)).isFalse();
        assertThat(GeoUtils.isValidLongitude(Double.NEGATIVE_INFINITY)).isFalse();
        assertThat(GeoUtils.isValidLongitude(null)).isFalse();
    }

    @Test
    @DisplayName("Unit: GeoUtils coordinates pair validation")
    void testCoordinatesPairValidation() {
        assertThat(GeoUtils.isValidCoordinates(23.3441, 85.3096)).isTrue();
        assertThat(GeoUtils.isValidCoordinates(90.0, 180.0)).isTrue();
        assertThat(GeoUtils.isValidCoordinates(-90.0, -180.0)).isTrue();

        // Invalid cases
        assertThat(GeoUtils.isValidCoordinates(91.0, 85.0)).isFalse();
        assertThat(GeoUtils.isValidCoordinates(23.0, 181.0)).isFalse();
        assertThat(GeoUtils.isValidCoordinates(null, 85.0)).isFalse();
        assertThat(GeoUtils.isValidCoordinates(23.0, null)).isFalse();
        assertThat(GeoUtils.isValidCoordinates(Double.NaN, 85.0)).isFalse();
        assertThat(GeoUtils.isValidCoordinates(23.0, Double.NaN)).isFalse();
    }

    @Test
    @DisplayName("Unit: GeoUtils distanceKm throws IllegalArgumentException on invalid coordinates")
    void testGeoUtilsDistanceSafety() {
        // Valid distance works
        Double dist = GeoUtils.distanceKm(23.3441, 85.3096, 23.3685, 85.3340);
        assertThat(dist).isNotNull().isGreaterThan(0.0);

        // Null coordinates return null
        assertThat(GeoUtils.distanceKm(null, 85.0, 23.0, 85.0)).isNull();
        assertThat(GeoUtils.distanceKm(23.0, null, 23.0, 85.0)).isNull();

        // Out-of-bounds throws IllegalArgumentException
        assertThatThrownBy(() -> GeoUtils.distanceKm(95.0, 85.0, 23.0, 85.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid coordinate bounds");

        assertThatThrownBy(() -> GeoUtils.distanceKm(23.0, 200.0, 23.0, 85.0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> GeoUtils.distanceKm(Double.NaN, 85.0, 23.0, 85.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Unit: GeoUtils isWithinRadius safely handles boundary and invalid inputs")
    void testIsWithinRadiusSafety() {
        // Ranchi points: Lalpur to Kanke Road (~5.3 km)
        assertThat(GeoUtils.isWithinRadius(23.3685, 85.3340, 23.4150, 85.3220, 10.0)).isTrue();
        assertThat(GeoUtils.isWithinRadius(23.3685, 85.3340, 23.4150, 85.3220, 2.0)).isFalse();

        // Invalid coordinates return false without throwing unhandled exceptions
        assertThat(GeoUtils.isWithinRadius(95.0, 85.0, 23.0, 85.0, 10.0)).isFalse();
        assertThat(GeoUtils.isWithinRadius(23.0, 200.0, 23.0, 85.0, 10.0)).isFalse();
        assertThat(GeoUtils.isWithinRadius(null, 85.0, 23.0, 85.0, 10.0)).isFalse();
        assertThat(GeoUtils.isWithinRadius(23.0, 85.0, 23.0, 85.0, -5.0)).isFalse();
        assertThat(GeoUtils.isWithinRadius(23.0, 85.0, 23.0, 85.0, null)).isFalse();
    }

    // ==============================================================
    // 2. INTEGRATION & BREAK TESTS: Controller Parameter Protection
    // ==============================================================

    @Test
    @DisplayName("Security/Break: /api/providers/nearby rejects invalid latitude > 90")
    void testNearbyRejectsLatitudeAbove90() throws Exception {
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "95.0")
                        .param("longitude", "85.3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180"));
    }

    @Test
    @DisplayName("Security/Break: /api/providers/nearby rejects invalid latitude < -90")
    void testNearbyRejectsLatitudeBelowMinus90() throws Exception {
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "-95.0")
                        .param("longitude", "85.3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180"));
    }

    @Test
    @DisplayName("Security/Break: /api/providers/nearby rejects invalid longitude > 180")
    void testNearbyRejectsLongitudeAbove180() throws Exception {
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "23.3")
                        .param("longitude", "185.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180"));
    }

    @Test
    @DisplayName("Security/Break: /api/providers/nearby rejects negative radiusKm")
    void testNearbyRejectsNegativeRadius() throws Exception {
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "23.3")
                        .param("longitude", "85.3")
                        .param("radiusKm", "-10.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Radius must be a positive number up to 1000 km"));
    }

    @Test
    @DisplayName("Security/Break: /api/providers/matching rejects out-of-bounds coordinates")
    void testMatchingRejectsOutOfBoundsCoords() throws Exception {
        mockMvc.perform(get("/api/providers/matching")
                        .param("serviceId", "1")
                        .param("latitude", "999.0")
                        .param("longitude", "85.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid coordinates: latitude must be between -90 and 90, longitude between -180 and 180"));
    }

    @Test
    @DisplayName("Security/Break: /api/providers/nearby accepts extreme boundary coordinates [-90, 90] and [-180, 180]")
    void testNearbyAcceptsValidBoundaries() throws Exception {
        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "90.0")
                        .param("longitude", "180.0"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/providers/nearby")
                        .param("latitude", "-90.0")
                        .param("longitude", "-180.0"))
                .andExpect(status().isOk());
    }

    // ==============================================================
    // 3. INTEGRATION & BREAK TESTS: Stored Coordinates (Booking & Profile)
    // ==============================================================

    @Test
    @DisplayName("Security/Break: Customer profile update rejects out-of-bounds latitude")
    void testCustomerProfileUpdateRejectsInvalidCoordinates() throws Exception {
        String token = registerUser("loc-cust-1@test.com", "CUSTOMER");

        String payload = """
            {
              "name": "Location Test Customer",
              "latitude": 91.5,
              "longitude": 85.3096
            }
            """;

        mockMvc.perform(put("/api/customer/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Security/Break: Provider profile update rejects out-of-bounds longitude")
    void testProviderProfileUpdateRejectsInvalidCoordinates() throws Exception {
        String token = registerUser("loc-prov-1@test.com", "PROVIDER");

        String payload = """
            {
              "businessName": "Location Test Electricals",
              "latitude": 23.3441,
              "longitude": 200.0
            }
            """;

        mockMvc.perform(put("/api/provider/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Security/Break: Booking creation rejects out-of-bounds coordinates")
    void testBookingCreationRejectsInvalidCoordinates() throws Exception {
        String token = registerUser("loc-book-cust@test.com", "CUSTOMER");

        String payload = """
            {
              "providerId": 1,
              "serviceId": 1,
              "bookingType": "QUICK_SERVICE",
              "address": "Main Road",
              "area": "Lalpur",
              "latitude": 95.0,
              "longitude": 85.3
            }
            """;

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ---- Helper method
    private String registerUser(String email, String role) throws Exception {
        String res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"name":"Test User","email":"%s","phone":"9876543210","password":"password123","role":"%s","businessName":"Test Biz"}
                        """.formatted(email, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.token");
    }
}
