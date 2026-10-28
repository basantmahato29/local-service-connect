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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration tests for Provider Profile Management,
 * Public Profile Visibility, Server-Side Validations, Privacy & RBAC Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderProfileManagementTest {

    @Autowired MockMvc mvc;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderAreaRepository providerAreaRepository;

    private ServiceType electricalService;
    private Long electricalServiceId;

    @BeforeEach
    void setUp() {
        ServiceType st = new ServiceType();
        st.setName("Electrical Care");
        st.setDescription("Professional electrician repairs and installations");
        electricalService = serviceTypeRepository.save(st);
        electricalServiceId = electricalService.getId();
    }

    @Test
    void providerRetrievesOwnProfileWithPersonalContactInfo() throws Exception {
        String token = registerUser("pro-owner@example.com", "PROVIDER", "Anand Sharma", "Sharma Electrical Solutions");

        // Authenticated provider calls /api/provider/profile
        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Sharma Electrical Solutions")))
                .andExpect(jsonPath("$.providerName", is("Anand Sharma")))
                .andExpect(jsonPath("$.providerEmail", is("pro-owner@example.com")))
                .andExpect(jsonPath("$.providerPhone", is("9876543210")))
                .andExpect(jsonPath("$.verificationStatus", is("PENDING")))
                .andExpect(jsonPath("$.availability", is(false)));
    }

    @Test
    void providerCanUpdatePersonalAndBusinessInformation() throws Exception {
        String token = registerUser("pro-update@example.com", "PROVIDER", "Vikram Das", "Das Home Repairs");

        String updatePayload = """
            {
                "name": "Vikram Das Sr.",
                "phone": "9123456789",
                "businessName": "Das Premier Services",
                "description": "Over 12 years of specialized home wiring and power backup setup.",
                "experienceYears": 12,
                "approxPrice": 399.00,
                "serviceRadiusKm": 25.0,
                "formattedAddress": "Main Road, Lalpur, Ranchi",
                "latitude": 23.3650,
                "longitude": 85.3280,
                "workingHours": "Mon - Sat: 9:00 AM - 7:00 PM",
                "serviceIds": [%d],
                "areas": [
                    {"area": "Lalpur", "city": "Ranchi"},
                    {"area": "Morabadi", "city": "Ranchi"}
                ]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Das Premier Services")))
                .andExpect(jsonPath("$.providerName", is("Vikram Das Sr.")))
                .andExpect(jsonPath("$.providerPhone", is("9123456789")))
                .andExpect(jsonPath("$.experienceYears", is(12)))
                .andExpect(jsonPath("$.approxPrice", is(399.0)))
                .andExpect(jsonPath("$.serviceRadiusKm", is(25.0)))
                .andExpect(jsonPath("$.areas", hasSize(2)))
                .andExpect(jsonPath("$.workingHours", is("Mon - Sat: 9:00 AM - 7:00 PM")));
    }

    @Test
    void serverValidatesAndRejectsNegativePrices() throws Exception {
        String token = registerUser("pro-negprice@example.com", "PROVIDER", "Price Tester", "Negative Price LLC");

        String invalidPayload = """
            {
                "businessName": "Negative Price LLC",
                "approxPrice": -150.00,
                "serviceIds": [%d]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverValidatesAndRejectsNegativeSubServicePrices() throws Exception {
        String token = registerUser("pro-subprice@example.com", "PROVIDER", "Sub Tester", "Sub Price LLC");

        String invalidPayload = """
            {
                "businessName": "Sub Price LLC",
                "approxPrice": 299.00,
                "serviceIds": [%d],
                "subServices": [
                    {
                        "name": "Invalid Negative Service",
                        "price": -50.00,
                        "duration": "30 mins",
                        "enabled": true
                    }
                ]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverValidatesAndRejectsInvalidCoordinates() throws Exception {
        String token = registerUser("pro-geo@example.com", "PROVIDER", "Geo Tester", "Geo LLC");

        String invalidPayload = """
            {
                "businessName": "Geo LLC",
                "latitude": 95.5,
                "longitude": 85.3280,
                "serviceIds": [%d]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverValidatesAndEnforcesMaxAreasLimit() throws Exception {
        String token = registerUser("pro-areas@example.com", "PROVIDER", "Area Tester", "Area LLC");

        StringBuilder areasJson = new StringBuilder("[");
        for (int i = 1; i <= 12; i++) {
            areasJson.append("{\"area\": \"Area ").append(i).append("\", \"city\": \"Ranchi\"}");
            if (i < 12) areasJson.append(",");
        }
        areasJson.append("]");

        String invalidPayload = """
            {
                "businessName": "Area LLC",
                "serviceIds": [%d],
                "areas": %s
            }
            """.formatted(electricalServiceId, areasJson.toString());

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicProfileVisibilityRulesAndPersonalPrivacyProtection() throws Exception {
        String token = registerUser("pro-public@example.com", "PROVIDER", "Sunil Verma", "Verma Electrical Hub");
        Provider p = providerRepository.findByUserEmail("pro-public@example.com").orElseThrow();

        // 1. Unverified provider returns 404 on public customer endpoint /api/providers/{id}
        mvc.perform(get("/api/providers/" + p.getId()))
                .andExpect(status().isNotFound());

        // 2. Admin verifies provider
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        providerRepository.save(p);

        // 3. Now verified provider is publicly accessible
        mvc.perform(get("/api/providers/" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName", is("Verma Electrical Hub")))
                .andExpect(jsonPath("$.verificationStatus", is("VERIFIED")))
                // Ensure personal email and phone are NOT exposed to public
                .andExpect(jsonPath("$.providerEmail").doesNotExist())
                .andExpect(jsonPath("$.providerPhone").doesNotExist());
    }

    @Test
    void unauthorizedUsersCannotAccessOrModifyProviderProfile() throws Exception {
        // Unauthenticated request -> 401
        mvc.perform(get("/api/provider/profile"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/provider/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\": \"Hacked Name\"}"))
                .andExpect(status().isUnauthorized());

        // Customer attempting to access provider profile -> 403
        String custToken = registerUser("customer-user@example.com", "CUSTOMER", "Customer A", null);

        mvc.perform(get("/api/provider/profile")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"businessName\": \"Customer Trying to Edit Provider\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotDirectlyAlterVerificationStatus() throws Exception {
        String token = registerUser("pro-audit@example.com", "PROVIDER", "Audit Candidate", "Audit Electric");
        Provider p = providerRepository.findByUserEmail("pro-audit@example.com").orElseThrow();
        assertEquals(VerificationStatus.PENDING, p.getVerificationStatus());

        // Provider attempts to set verificationStatus in profile update payload
        String tamperingPayload = """
            {
                "businessName": "Audit Electric",
                "verificationStatus": "VERIFIED",
                "serviceIds": [%d]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tamperingPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus", is("PENDING")));

        // Verify database entity is still PENDING
        Provider reloaded = providerRepository.findById(p.getId()).orElseThrow();
        assertEquals(VerificationStatus.PENDING, reloaded.getVerificationStatus());
    }

    @Test
    void providerManagesSubServicesSuccessfully() throws Exception {
        String token = registerUser("pro-subservices@example.com", "PROVIDER", "Sub Boss", "Boss Electric Services");

        String subServicesPayload = """
            {
                "businessName": "Boss Electric Services",
                "approxPrice": 199.00,
                "serviceIds": [%d],
                "subServices": [
                    {
                        "name": "Ceiling Fan Repair & Balance",
                        "category": "Electrician",
                        "description": "Blades alignment and capacitor replacement",
                        "price": 249.00,
                        "duration": "30 mins",
                        "enabled": true
                    },
                    {
                        "name": "Full House MCB Diagnosis",
                        "category": "Electrician",
                        "description": "Short circuit inspection and phase balancing",
                        "price": 599.00,
                        "duration": "60 mins",
                        "enabled": true
                    }
                ]
            }
            """.formatted(electricalServiceId);

        mvc.perform(put("/api/provider/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(subServicesPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subServices", hasSize(2)))
                .andExpect(jsonPath("$.subServices[0].name", is("Ceiling Fan Repair & Balance")))
                .andExpect(jsonPath("$.subServices[0].price", is(249.0)))
                .andExpect(jsonPath("$.subServices[1].name", is("Full House MCB Diagnosis")))
                .andExpect(jsonPath("$.subServices[1].price", is(599.0)));
    }

    @Test
    void providerAvailabilityRules() throws Exception {
        String token = registerUser("pro-avail@example.com", "PROVIDER", "Avail Worker", "Avail Repairs");
        Provider p = providerRepository.findByUserEmail("pro-avail@example.com").orElseThrow();

        // 1. Unverified provider cannot go online -> 403 FORBIDDEN
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
                .andExpect(status().isForbidden());

        // 2. Admin verifies provider
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.getServices().add(electricalService);
        providerRepository.save(p);

        ProviderArea pa = new ProviderArea();
        pa.setProviderId(p.getId());
        pa.setArea("Lalpur");
        pa.setCity("Ranchi");
        providerAreaRepository.save(pa);

        // 3. Now verified provider can toggle availability online
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(true)));

        // 4. Provider toggles availability offline
        mvc.perform(put("/api/provider/availability")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"available\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability", is(false)));
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
