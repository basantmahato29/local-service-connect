package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminVerificationControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String adminToken;
    private String customerToken;
    private String providerToken;

    @BeforeEach
    void setUp() throws Exception {
        // Admin token
        String adminLogin = """
            {"email":"admin@test.com","password":"Admin@12345"}
            """;
        String adminRes = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminLogin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = JsonPath.read(adminRes, "$.token");

        // Customer token
        String custReg = """
            {"name":"Test Customer","email":"cust_%d@test.com","phone":"9876543210","password":"password123","role":"CUSTOMER"}
            """.formatted(System.currentTimeMillis());
        String custRes = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(custReg))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customerToken = JsonPath.read(custRes, "$.token");

        // Provider token
        String provReg = """
            {"name":"Test Provider","email":"prov_%d@test.com","phone":"9876543211","password":"password123","role":"PROVIDER","businessName":"Test Biz"}
            """.formatted(System.currentTimeMillis());
        String provRes = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(provReg))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        providerToken = JsonPath.read(provRes, "$.token");
    }

    @Test
    void unauthenticatedAccessToApplicationsIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/verification/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessVerificationApplications() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotAccessVerificationApplications() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListApplicationsUnpaginatedReturnsArray() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListApplicationsWithPaginationReturnsPageObject() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.number", is(0)))
                .andExpect(jsonPath("$.size", is(5)));
    }

    @Test
    void adminCanFilterByStatus() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanFilterByPendingStatusAlias() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "5")
                .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCanFilterByType() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("type", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminSearchWithNoMatchReturnsEmptyResults() throws Exception {
        // Unpaginated empty result
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("search", "DEFINITELY_NONEXISTENT_PROVIDER_XYZ_99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Paginated empty result
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("search", "DEFINITELY_NONEXISTENT_PROVIDER_XYZ_99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    void adminCanSortApplicationsPaginated() throws Exception {
        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "5")
                .param("sortBy", "submittedAt")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.number", is(0)));

        mvc.perform(get("/api/admin/verification/applications")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "5")
                .param("sortBy", "legalName")
                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.number", is(0)));
    }

    @Test
    void adminCanGetSummaryCounts() throws Exception {
        mvc.perform(get("/api/admin/verification/applications/summary-counts")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.TOTAL").isNumber())
                .andExpect(jsonPath("$.SUBMITTED").isNumber())
                .andExpect(jsonPath("$.UNDER_REVIEW").isNumber())
                .andExpect(jsonPath("$.VERIFIED").isNumber());
    }
}

