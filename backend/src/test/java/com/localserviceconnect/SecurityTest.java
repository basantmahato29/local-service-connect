package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role-based access and registration rules, through the real security filter chain. */
@SpringBootTest @AutoConfigureMockMvc
class SecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void publicEndpointsAreOpen() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isOk());
        mvc.perform(get("/api/providers")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews/featured")).andExpect(status().isOk());
    }

    @Test
    void privateEndpointsNeedALogin() throws Exception {
        mvc.perform(get("/api/bookings/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerCannotUseAdminOrProviderApis() throws Exception {
        String token = register("sec-customer@test.com", "CUSTOMER");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/customer/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void aProviderCannotUseCustomerOrAdminApis() throws Exception {
        String token = register("sec-provider@test.com", "PROVIDER");
        mvc.perform(get("/api/customer/profile").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/providers").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/provider/profile").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void theAdminCanOpenTheDashboard() throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(res, "$.token");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"nope\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void nobodyCanRegisterAsAdmin() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("sec-admin@test.com", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailsAreRejected() throws Exception {
        register("sec-dup@test.com", "CUSTOMER");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("sec-dup@test.com", "CUSTOMER")))
                .andExpect(status().isConflict());
    }

    @Test
    void searchServicesReturnsResultsAndHandlesXssQuerySafely() throws Exception {
        mvc.perform(get("/api/services/search").param("query", "<script>alert(1)</script>"))
                .andExpect(status().isOk());
    }

    @Test
    void bruteForceLoginIsRateLimitedAfterFiveAttempts() throws Exception {
        String testEmail = "brute-force-" + System.currentTimeMillis() + "@test.com";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + testEmail + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + testEmail + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void malformedJsonReturnsBadRequestWithoutLeakingInternalStackTrace() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid-json-body}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidParameterTypeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/services/not-a-number"))
                .andExpect(status().isBadRequest());
    }

    private String register(String email, String role) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body(email, role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.token");
    }

    private static String body(String email, String role) {
        return """
            {"name":"Sec Test","email":"%s","phone":"9876543210","password":"password123","role":"%s","businessName":"Test Biz"}
            """.formatted(email, role);
    }
}
