package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import com.localserviceconnect.entity.Provider;
import com.localserviceconnect.entity.ProviderArea;
import com.localserviceconnect.entity.ServiceType;
import com.localserviceconnect.entity.VerificationStatus;
import com.localserviceconnect.repository.ProviderAreaRepository;
import com.localserviceconnect.repository.ProviderRepository;
import com.localserviceconnect.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the dedicated Provider Homepage, role-based login routing,
 * opportunities board, Quick Service availability management, and RBAC defense.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderHomepageWorkflowTest {

    @Autowired MockMvc mvc;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderAreaRepository providerAreaRepository;

    private Long serviceTypeId;
    private ServiceType savedService;

    @BeforeEach
    void setUp() {
        ServiceType st = new ServiceType();
        st.setName("Home Appliances Repair");
        st.setDescription("Professional appliance diagnostics and repair");
        savedService = serviceTypeRepository.save(st);
        serviceTypeId = savedService.getId();
    }

    @Test
    void providerHomeAndDashboardStaticRoutesAreAccessible() throws Exception {
        // Forwarding routes
        mvc.perform(get("/provider/home")).andExpect(status().isOk());
        mvc.perform(get("/provider/dashboard")).andExpect(status().isOk());

        // Direct HTML assets
        mvc.perform(get("/provider/home.html")).andExpect(status().isOk());
        mvc.perform(get("/provider-dashboard.html")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void providerAuthEnablesAccessToProfileAndPerformanceMetrics() throws Exception {
        String token = registerUser("pro-homepage@example.com", "PROVIDER", "Home Pro", "Elite Fixers LLC");

        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Elite Fixers LLC")))
                .andExpect(jsonPath("$.verificationStatus", is("PENDING")))
                .andExpect(jsonPath("$.availability", is(false)));
    }

    @Test
    void opportunitiesFeedDisplaysPendingRequestsWithCustomerDataMasked() throws Exception {
        // 1. Setup verified provider
        String proToken = registerUser("pro-opps@example.com", "PROVIDER", "Opp Pro", "Opp Services");
        String proProfileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long providerId = ((Number) JsonPath.read(proProfileRes, "$.id")).longValue();
        verifyAndSetupProvider(providerId, "Lalpur");

        // 2. Customer creates a booking
        String custToken = registerUser("cust-opps@example.com", "CUSTOMER", "Private Cust", null);
        String bookingPayload = """
            {
                "providerId": %d,
                "serviceId": %d,
                "bookingType": "QUICK_SERVICE",
                "address": "Confidential Apartment 4B",
                "area": "Lalpur",
                "notes": "Urgent appliance leakage",
                "latitude": 23.3500,
                "longitude": 85.3200
            }
            """.formatted(providerId, serviceTypeId);

        String createBookingRes = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long bookingId = ((Number) JsonPath.read(createBookingRes, "$.id")).longValue();

        // 3. Provider fetches opportunities from /api/bookings/my
        mvc.perform(get("/api/bookings/my")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].id", is(bookingId.intValue())))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].bookingType", is("QUICK_SERVICE")))
                .andExpect(jsonPath("$[0].area", is("Lalpur")))
                // Ensure customer phone is masked or null in PENDING state
                .andExpect(jsonPath("$[0].customerPhone", anyOf(nullValue(), is(""))));

        // 4. Provider accepts the opportunity
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/accept")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")));
    }

    @Test
    void quickServiceAvailabilityManagementRuleEnforcement() throws Exception {
        // 1. Unverified provider cannot go online
        String unverifiedToken = registerUser("pro-unverif-hub@example.com", "PROVIDER", "Unverif Pro", "Unverif Hub");
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + unverifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("An admin must verify your account")));

        // 2. Verified provider can toggle online and offline
        String verifiedToken = registerUser("pro-verif-hub@example.com", "PROVIDER", "Verif Pro", "Verif Hub");
        String profileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + verifiedToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long providerId = ((Number) JsonPath.read(profileRes, "$.id")).longValue();
        verifyAndSetupProvider(providerId, "Lalpur");

        // Toggle to online
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + verifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(true)));

        // Toggle back to offline
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + verifiedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(false)));
    }

    @Test
    void preserveRoleSeparationBetweenCustomerProviderAndAdmin() throws Exception {
        String custToken = registerUser("cust-rbac-hub@example.com", "CUSTOMER", "Customer User", null);
        String proToken = registerUser("pro-rbac-hub@example.com", "PROVIDER", "Provider User", "Provider Hub");

        // Customer cannot access provider endpoints
        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isForbidden());

        // Provider cannot access customer or admin endpoints
        mvc.perform(get("/api/customer/profile")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void offlineProviderIsExcludedFromQuickServiceMatching() throws Exception {
        String proToken = registerUser("pro-qs-offline@example.com", "PROVIDER", "QS Pro", "Quick Dispatch LLC");
        String profileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long providerId = ((Number) JsonPath.read(profileRes, "$.id")).longValue();
        verifyAndSetupProvider(providerId, "Lalpur");

        // 1. Initially toggle provider to OFFLINE
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + proToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(false)));

        // Matching query for QUICK_SERVICE must not return this offline provider
        mvc.perform(get("/api/providers/matching")
                .param("serviceId", serviceTypeId.toString())
                .param("area", "Lalpur")
                .param("type", "QUICK_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + providerId + ")]", hasSize(0)));

        // 2. Toggle provider back to ONLINE
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + proToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(true)));

        // Matching query for QUICK_SERVICE must now find the online provider
        mvc.perform(get("/api/providers/matching")
                .param("serviceId", serviceTypeId.toString())
                .param("area", "Lalpur")
                .param("type", "QUICK_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + providerId + ")]", hasSize(1)));
    }

    @Test
    void providerWithoutServicesOrAreasCannotGoOnline() throws Exception {
        // Register provider but do not assign any services or areas
        String emptyProToken = registerUser("pro-empty-setup@example.com", "PROVIDER", "Empty Pro", "Empty Services");
        String profileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + emptyProToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long providerId = ((Number) JsonPath.read(profileRes, "$.id")).longValue();

        // Mark as verified but keep services and areas empty
        Provider p = providerRepository.findById(providerId).orElseThrow();
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        providerRepository.save(p);

        // Attempting to go online must fail with 400 Bad Request
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + emptyProToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Add at least one service and one service area or location first")));
    }

    @Test
    void unauthenticatedRequestsToAvailabilityAreRejected() throws Exception {
        mvc.perform(put("/api/provider/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isUnauthorized());
    }

    private void verifyAndSetupProvider(Long providerId, String area) {
        Provider p = providerRepository.findById(providerId).orElseThrow();
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.setAvailability(true);
        p.getServices().add(savedService);
        providerRepository.save(p);

        ProviderArea pa = new ProviderArea();
        pa.setProviderId(providerId);
        pa.setArea(area);
        pa.setCity("Ranchi");
        providerAreaRepository.save(pa);
    }

    private String registerUser(String email, String role, String name, String businessName) throws Exception {
        String biz = businessName != null ? businessName : "Default Biz";
        String payload = """
            {
                "name": "%s",
                "email": "%s",
                "phone": "9876543210",
                "password": "Password@123",
                "role": "%s",
                "businessName": "%s"
            }
            """.formatted(name, email, role, biz);

        String res = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(res, "$.token");
    }

    @Test
    void testPhotoUploadWorkflow() throws Exception {
        String token = registerUser("uploader@provider.com", "PROVIDER", "Uploader", "Uploader Biz");

        // 1. Valid PNG upload via PUT /api/provider/photo
        byte[] validPng = new byte[]{ (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52 };
        String pngBase64 = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(validPng);

        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(pngBase64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(pngBase64));

        // Verify photo persisted in profile
        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(pngBase64));

        // 2. Valid JPEG upload
        byte[] validJpeg = new byte[]{ (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0, 16, 0x4A, 0x46, 0x49, 0x46, 0, 1 };
        String jpegBase64 = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(validJpeg);

        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(jpegBase64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(jpegBase64));

        // 3. Valid WebP upload
        byte[] validWebp = new byte[]{ 'R', 'I', 'F', 'F', 20, 0, 0, 0, 'W', 'E', 'B', 'P', 0, 0, 0, 0 };
        String webpBase64 = "data:image/webp;base64," + java.util.Base64.getEncoder().encodeToString(validWebp);

        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(webpBase64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(webpBase64));

        // 4. Valid HTTP URL
        String httpUrl = "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?w=400";
        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(httpUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(httpUrl));

        // 5. Unsupported format (GIF) -> 400
        byte[] gifBytes = new byte[]{ 'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0 };
        String gifBase64 = "data:image/gif;base64," + java.util.Base64.getEncoder().encodeToString(gifBytes);
        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(gifBase64)))
                .andExpect(status().isBadRequest());

        // 6. Fake format header mismatch (Declared PNG but with garbage header) -> 400
        byte[] fakeBytes = new byte[]{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
        String fakeBase64 = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(fakeBytes);
        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(fakeBase64)))
                .andExpect(status().isBadRequest());

        // 7. Oversized file (> 5MB) -> 400
        byte[] oversized = new byte[5 * 1024 * 1024 + 1024];
        oversized[0] = (byte)0x89; oversized[1] = 0x50; oversized[2] = 0x4E; oversized[3] = 0x47;
        String oversizedBase64 = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(oversized);
        mvc.perform(put("/api/provider/photo")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "profileImage": "%s" }
                    """.formatted(oversizedBase64)))
                .andExpect(status().isBadRequest());

        // 8. Updating photo via full profile update PUT /api/provider/profile
        String profileUpdate = """
            {
                "businessName": "Uploader Biz Updated",
                "description": "Updated description",
                "experienceYears": 4,
                "approxPrice": 150.0,
                "serviceIds": [%d],
                "areas": [],
                "profileImage": "%s"
            }
            """.formatted(serviceTypeId, webpBase64);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(profileUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(webpBase64));
    }

    @Test
    void providerNavbarAndStaticNavigationRoutesVerifyCorrectTargets() throws Exception {
        // 1. Verify provider routes forward to valid pages
        mvc.perform(get("/provider/home")).andExpect(status().isOk());
        mvc.perform(get("/provider/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/provider/home.html")).andExpect(status().isOk());
        mvc.perform(get("/provider-dashboard.html")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/my-account.html")).andExpect(status().isOk());

        // 2. Verify api.js contains role-aware provider Home navigation to /provider/home
        String apiJs = mvc.perform(get("/js/api.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Must link provider home to /provider/home
        org.junit.jupiter.api.Assertions.assertTrue(
                apiJs.contains("href=\"/provider/home\""),
                "api.js must route provider Home to /provider/home"
        );
        // Must NOT link provider desktop Home to root '/'
        org.junit.jupiter.api.Assertions.assertFalse(
                apiJs.contains("${isProvider ? `\n            <a class=\"nav-link\" href=\"/\">"),
                "api.js provider desktop navbar must not point Home to '/'"
        );
    }
}
