package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String customerToken;
    private String providerToken;

    @BeforeEach
    void setUp() throws Exception {
        customerToken = registerOrLogin("rev_cust_" + System.currentTimeMillis() + "@test.com", "CUSTOMER");
        providerToken = registerOrLogin("rev_prov_" + System.currentTimeMillis() + "@test.com", "PROVIDER");
    }

    @Test
    void publicReviewEndpointsAreAccessibleWithoutAuth() throws Exception {
        mvc.perform(get("/api/reviews/featured")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews/provider/1")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews").param("providerId", "1")).andExpect(status().isOk());
    }

    @Test
    void getReviewsWithoutProviderIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/reviews")).andExpect(status().isBadRequest());
    }

    @Test
    void creatingReviewRequiresAuthentication() throws Exception {
        String payload = """
            {"bookingId": 1, "rating": 5, "review": "Excellent work"}
            """;
        mvc.perform(post("/api/reviews")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingReviewWithInvalidRatingFailsValidation() throws Exception {
        String invalidRatingPayload = """
            {"bookingId": 1, "rating": 6, "review": "Too high rating"}
            """;
        mvc.perform(post("/api/reviews")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRatingPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingReviewWithMissingBookingIdFailsValidation() throws Exception {
        String missingBookingPayload = """
            {"rating": 4, "review": "Missing booking id"}
            """;
        mvc.perform(post("/api/reviews")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingBookingPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCanFetchTheirOwnReviews() throws Exception {
        mvc.perform(get("/api/customer/reviews")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());
    }

    @Test
    void providerCanFetchTheirOwnReviews() throws Exception {
        mvc.perform(get("/api/provider/reviews")
                .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isOk());
    }

    @Test
    void getReviewForNonExistentBookingReturnsNoContent() throws Exception {
        mvc.perform(get("/api/reviews/booking/999999"))
                .andExpect(status().isNoContent());
    }

    private String registerOrLogin(String email, String role) throws Exception {
        String body = """
            {"name":"Test User","email":"%s","phone":"9876543210","password":"password123","role":"%s","businessName":"Test Biz"}
            """.formatted(email, role);
        String res = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.token");
    }
}
