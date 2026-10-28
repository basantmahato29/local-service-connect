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
 * End-to-end integration tests for Provider Dashboard workflows and Role-Based Access Control (RBAC).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderDashboardWorkflowTest {

    @Autowired MockMvc mvc;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderAreaRepository providerAreaRepository;

    private Long serviceTypeId;
    private ServiceType savedService;

    @BeforeEach
    void setUp() {
        ServiceType st = new ServiceType();
        st.setName("Electrical Services");
        st.setDescription("Professional electrician repairs and installations");
        savedService = serviceTypeRepository.save(st);
        serviceTypeId = savedService.getId();
    }

    @Test
    void providerCanLoginAndAccessProfile() throws Exception {
        String token = registerUser("pro-worker@example.com", "PROVIDER", "Pro Worker", "Pro Electrics LLC");

        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Pro Electrics LLC")))
                .andExpect(jsonPath("$.verificationStatus", is("PENDING")))
                .andExpect(jsonPath("$.availability", is(false)));
    }

    @Test
    void unverifiedProviderCannotGoOnline() throws Exception {
        String token = registerUser("pro-unverified@example.com", "PROVIDER", "Unverified Pro", "Unverified LLC");

        // Attempting to go online before verification must be forbidden
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("An admin must verify your account")));
    }

    @Test
    void providerCanUpdateProfileDetailsAndAvailability() throws Exception {
        String token = registerUser("pro-toggle@example.com", "PROVIDER", "Toggle Pro", "Toggle Services");
        String proProfileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number proIdNum = JsonPath.read(proProfileRes, "$.id");
        Long providerId = proIdNum.longValue();

        // Verify provider so they can go online
        verifyAndSetupProvider(providerId, "Lalpur");

        // 1. Toggle availability to true
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(true)));

        // 2. Update profile details
        String updatePayload = """
            {
                "businessName": "Toggle Master Services",
                "description": "Certified electrician with 10 years experience",
                "experienceYears": 10,
                "approxPrice": 350.0,
                "serviceIds": [%d],
                "areas": [{"area":"Lalpur","city":"Ranchi"}],
                "formattedAddress": "45 MG Road, Ranchi",
                "latitude": 23.3441,
                "longitude": 85.3096,
                "serviceRadiusKm": 15.0,
                "workingHours": "Mon-Sun 8AM-8PM"
            }
            """.formatted(serviceTypeId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Toggle Master Services")))
                .andExpect(jsonPath("$.approxPrice", is(350.0)))
                .andExpect(jsonPath("$.latitude", is(23.3441)))
                .andExpect(jsonPath("$.longitude", is(85.3096)));

        // 3. Toggle availability to false
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(false)));
    }

    @Test
    void providerBookingLifecycleEndToEnd() throws Exception {
        // Register provider
        String proToken = registerUser("pro-lifecycle@example.com", "PROVIDER", "Lifecycle Pro", "Lifecycle Electric");
        String proProfileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number proIdNum = JsonPath.read(proProfileRes, "$.id");
        Long providerId = proIdNum.longValue();

        // Verify and activate provider for matching
        verifyAndSetupProvider(providerId, "Lalpur");

        // Register customer
        String custToken = registerUser("cust-booking@example.com", "CUSTOMER", "Booking Cust", null);

        // 1. Customer creates a booking
        String futureDate = LocalDate.now().plusDays(2).toString();
        String bookingPayload = """
            {
                "providerId": %d,
                "serviceId": %d,
                "bookingType": "NORMAL",
                "bookingDate": "%s",
                "bookingTime": "14:00:00",
                "address": "45 Main Road",
                "area": "Lalpur",
                "notes": "Emergency switchboard repair",
                "latitude": 23.3500,
                "longitude": 85.3200
            }
            """.formatted(providerId, serviceTypeId, futureDate);

        String createBookingRes = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Number bookingIdNum = JsonPath.read(createBookingRes, "$.id");
        Long bookingId = bookingIdNum.longValue();

        // 2. Provider checks my bookings
        mvc.perform(get("/api/bookings/my")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].status", is("PENDING")));

        // 3. Provider accepts booking
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/accept")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")));

        // 4. Provider advances to ON_THE_WAY
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/status")
                .header("Authorization", "Bearer " + proToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ON_THE_WAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ON_THE_WAY")));

        // 5. Customer fetches OTP
        String otpRes = mvc.perform(get("/api/bookings/" + bookingId + "/otp")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otp", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String otp = JsonPath.read(otpRes, "$.otp");

        // 6. Provider verifies OTP to start job -> SERVICE_STARTED
        mvc.perform(post("/api/provider/bookings/" + bookingId + "/otp/verify")
                .header("Authorization", "Bearer " + proToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + otp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SERVICE_STARTED")));

        // 7. Provider advances to AWAITING_COMPLETION
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/status")
                .header("Authorization", "Bearer " + proToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"AWAITING_COMPLETION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AWAITING_COMPLETION")));

        // 8. Customer confirms completion
        mvc.perform(post("/api/bookings/" + bookingId + "/completion/confirm")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")));
    }

    @Test
    void providerCanRejectBookingRequest() throws Exception {
        String proToken = registerUser("pro-reject@example.com", "PROVIDER", "Reject Pro", "Reject Repairs");
        String proProfileRes = mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number proIdNum = JsonPath.read(proProfileRes, "$.id");
        Long providerId = proIdNum.longValue();

        verifyAndSetupProvider(providerId, "Lalpur");

        String custToken = registerUser("cust-reject@example.com", "CUSTOMER", "Reject Cust", null);

        String futureDate = LocalDate.now().plusDays(3).toString();
        String bookingPayload = """
            {
                "providerId": %d,
                "serviceId": %d,
                "bookingType": "NORMAL",
                "bookingDate": "%s",
                "bookingTime": "15:00:00",
                "address": "12 Line Tank Road",
                "area": "Lalpur",
                "notes": "Inspection request",
                "latitude": 23.3600,
                "longitude": 85.3100
            }
            """.formatted(providerId, serviceTypeId, futureDate);

        String createBookingRes = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number bookingIdNum = JsonPath.read(createBookingRes, "$.id");
        Long bookingId = bookingIdNum.longValue();

        // Provider rejects the booking -> REJECTED
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/reject")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }

    @Test
    void customerCannotAccessProviderProtectedEndpoints() throws Exception {
        String custToken = registerUser("cust-forbidden@example.com", "CUSTOMER", "Unauthorized Cust", null);

        // Customer calling provider profile -> 403
        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());

        // Customer calling provider availability -> 403
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isForbidden());

        // Customer calling provider reviews -> 403
        mvc.perform(get("/api/provider/reviews")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());

        // Customer calling provider booking accept -> 403
        mvc.perform(put("/api/provider/bookings/1/accept")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotAccessCustomerProtectedEndpoints() throws Exception {
        String proToken = registerUser("pro-forbidden@example.com", "PROVIDER", "Pro User", "Pro Fix");

        // Provider calling customer profile -> 403
        mvc.perform(get("/api/customer/profile")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isForbidden());

        // Provider calling customer reviews -> 403
        mvc.perform(get("/api/customer/reviews")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isForbidden());

        // Provider calling admin endpoints -> 403
        mvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/provider/profile"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/provider/availability")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\":true}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/bookings/my"))
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
}
