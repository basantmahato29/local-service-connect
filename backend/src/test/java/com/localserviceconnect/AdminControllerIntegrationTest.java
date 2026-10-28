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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        String adminLogin = """
            {"email":"admin@test.com","password":"Admin@12345"}
            """;
        String adminRes = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adminLogin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = JsonPath.read(adminRes, "$.token");

        String custReg = """
            {"name":"AdminTest Customer","email":"admin_cust_%d@test.com","phone":"9876543212","password":"password123","role":"CUSTOMER"}
            """.formatted(System.currentTimeMillis());
        String custRes = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(custReg))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customerToken = JsonPath.read(custRes, "$.token");

        String provReg = """
            {"name":"AdminTest Provider","email":"admin_prov_%d@test.com","phone":"9876543211","password":"password123","role":"PROVIDER","businessName":"Test Plumbing Services"}
            """.formatted(System.currentTimeMillis());
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(provReg))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedAccessToDashboardReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerAccessToDashboardReturnsForbidden() throws Exception {
        mvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessDashboard() throws Exception {
        mvc.perform(get("/api/admin/dashboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andExpect(jsonPath("$.totalProviders").isNumber())
                .andExpect(jsonPath("$.verifiedProviders").isNumber())
                .andExpect(jsonPath("$.pendingProviders").isNumber())
                .andExpect(jsonPath("$.totalServices").isNumber())
                .andExpect(jsonPath("$.totalBookings").isNumber())
                .andExpect(jsonPath("$.activeBookings").isNumber())
                .andExpect(jsonPath("$.completedBookings").isNumber())
                .andExpect(jsonPath("$.cancelledBookings").isNumber())
                .andExpect(jsonPath("$.quickServiceBookings").isNumber())
                .andExpect(jsonPath("$.bookingsByStatus").isMap());
    }

    @Test
    void adminCanFilterBookingsByActive() throws Exception {
        mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanFilterBookingsByType() throws Exception {
        mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("type", "QUICK_SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListCustomers() throws Exception {
        mvc.perform(get("/api/admin/customers")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListProviders() throws Exception {
        mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListServices() throws Exception {
        mvc.perform(get("/api/admin/services")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListReviews() throws Exception {
        mvc.perform(get("/api/admin/reviews")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanListProvidersPaged() throws Exception {
        mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "createdAt")
                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void adminCanFilterProvidersByStatusAndAvailability() throws Exception {
        mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("status", "VERIFIED")
                .param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCanSearchProviders() throws Exception {
        mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("search", "plumb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCanGetProviderDetailsAndHistory() throws Exception {
        String provListRes = mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number providerIdNum = JsonPath.read(provListRes, "$.content[0].provider.id");
        long providerId = providerIdNum.longValue();

        mvc.perform(get("/api/admin/providers/" + providerId + "/details")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider.id").value(providerId))
                .andExpect(jsonPath("$.ownerName").isNotEmpty())
                .andExpect(jsonPath("$.totalBookings").isNumber())
                .andExpect(jsonPath("$.completedBookings").isNumber())
                .andExpect(jsonPath("$.activeBookingsList").isArray())
                .andExpect(jsonPath("$.actionHistory").isArray());

        mvc.perform(get("/api/admin/providers/" + providerId + "/history")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void adminCanSuspendAndReinstateProviderWithReason() throws Exception {
        String provListRes = mvc.perform(get("/api/admin/providers")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number providerIdNum = JsonPath.read(provListRes, "$.content[0].provider.id");
        long providerId = providerIdNum.longValue();

        // 1. Suspend with recorded reason
        String suspendBody = """
            {"enabled":false,"reason":"Suspected terms violation reported by customer"}
            """;
        mvc.perform(put("/api/admin/providers/" + providerId + "/enabled")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(suspendBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Verify audit log has the SUSPEND entry
        mvc.perform(get("/api/admin/providers/" + providerId + "/history")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SUSPEND"))
                .andExpect(jsonPath("$[0].reason").value("Suspected terms violation reported by customer"));

        // 2. Reinstate with recorded reason
        String reinstateBody = """
            {"enabled":true,"reason":"Investigation complete - provider re-verified and account reinstated"}
            """;
        mvc.perform(put("/api/admin/providers/" + providerId + "/enabled")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reinstateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Verify audit log has REINSTATE entry as latest
        mvc.perform(get("/api/admin/providers/" + providerId + "/history")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("REINSTATE"))
                .andExpect(jsonPath("$[0].reason").value("Investigation complete - provider re-verified and account reinstated"));
    }

    @Test
    void adminCanAccessQuickServiceMonitor() throws Exception {
        mvc.perform(get("/api/admin/quick-service/monitor")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveRequests").isNumber())
                .andExpect(jsonPath("$.pendingAssignmentCount").isNumber())
                .andExpect(jsonPath("$.inProgressCount").isNumber())
                .andExpect(jsonPath("$.delayedCount").isNumber())
                .andExpect(jsonPath("$.requests").isArray());
    }

    @Test
    void customerAccessToQuickServiceMonitorReturnsForbidden() throws Exception {
        mvc.perform(get("/api/admin/quick-service/monitor")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListBookingsPaged() throws Exception {
        mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("sortBy", "createdAt")
                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void adminCanSearchAndFilterBookings() throws Exception {
        mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "10")
                .param("search", "plumb")
                .param("type", "NORMAL")
                .param("fromDate", "2020-01-01")
                .param("toDate", "2030-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void adminCanGetBookingDetailsAndHistory() throws Exception {
        String bookingsRes = mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number total = JsonPath.read(bookingsRes, "$.totalElements");
        if (total.intValue() > 0) {
            Number bookingIdNum = JsonPath.read(bookingsRes, "$.content[0].id");
            long bookingId = bookingIdNum.longValue();

            mvc.perform(get("/api/admin/bookings/" + bookingId + "/details")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.booking.id").value(bookingId))
                    .andExpect(jsonPath("$.timeline").isArray());

            mvc.perform(get("/api/admin/bookings/" + bookingId + "/history")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Test
    void adminCanUpdateBookingStatusWithMandatoryReason() throws Exception {
        String bookingsRes = mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number total = JsonPath.read(bookingsRes, "$.totalElements");
        if (total.intValue() > 0) {
            Number bookingIdNum = JsonPath.read(bookingsRes, "$.content[0].id");
            long bookingId = bookingIdNum.longValue();

            // 1. Missing reason must fail (400)
            String badReq = """
                {"status":"CANCELLED","reason":""}
                """;
            mvc.perform(put("/api/admin/bookings/" + bookingId + "/status")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(badReq))
                    .andExpect(status().isBadRequest());

            // 2. Valid request with recorded explanation must succeed
            String validReq = """
                {"status":"CANCELLED","reason":"Customer requested cancellation via admin phone support"}
                """;
            mvc.perform(put("/api/admin/bookings/" + bookingId + "/status")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validReq))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancellationReason").value("Customer requested cancellation via admin phone support"));

            // 3. Verify status history audit trail captures the transition
            mvc.perform(get("/api/admin/bookings/" + bookingId + "/history")
                    .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].action").value("ADMIN_CANCEL"))
                    .andExpect(jsonPath("$[0].reason").value("Customer requested cancellation via admin phone support"))
                    .andExpect(jsonPath("$[0].actorRole").value("ROLE_ADMIN"));
        }
    }

    @Test
    void adminCanAccessBookingAnalytics() throws Exception {
        mvc.perform(get("/api/admin/bookings/analytics")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").isNumber())
                .andExpect(jsonPath("$.activeBookings").isNumber())
                .andExpect(jsonPath("$.completedBookings").isNumber())
                .andExpect(jsonPath("$.cancelledBookings").isNumber())
                .andExpect(jsonPath("$.bookingsByStatus").isMap())
                .andExpect(jsonPath("$.dailyTrends").isArray());
    }

    @Test
    void nonAdminCannotAccessBookingAdminEndpoints() throws Exception {
        mvc.perform(get("/api/admin/bookings")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/bookings/analytics")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessFullPlatformAnalytics() throws Exception {
        mvc.perform(get("/api/admin/analytics")
                .header("Authorization", "Bearer " + adminToken)
                .param("period", "30d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalCustomers").isNumber())
                .andExpect(jsonPath("$.overview.totalProviders").isNumber())
                .andExpect(jsonPath("$.overview.activeBookings").isNumber())
                .andExpect(jsonPath("$.dateRange.period").value("30d"))
                .andExpect(jsonPath("$.dateRange.daysCount").value(30))
                .andExpect(jsonPath("$.dailyTrends").isArray())
                .andExpect(jsonPath("$.statusBreakdown").isMap())
                .andExpect(jsonPath("$.typeComparison.normalCount").isNumber())
                .andExpect(jsonPath("$.typeComparison.quickServiceCount").isNumber())
                .andExpect(jsonPath("$.providerPerformance.topRated").isArray())
                .andExpect(jsonPath("$.providerPerformance.mostBooked").isArray())
                .andExpect(jsonPath("$.providerPerformance.mostCompleted").isArray())
                .andExpect(jsonPath("$.serviceDemand.popularCategories").isArray())
                .andExpect(jsonPath("$.serviceDemand.areaDemand").isArray())
                .andExpect(jsonPath("$.serviceDemand.supplyGaps").isArray())
                .andExpect(jsonPath("$.quickServicePerformance.totalRequests").isNumber())
                .andExpect(jsonPath("$.quickServicePerformance.delayedCount").isNumber());
    }

    @Test
    void adminCanFilterAnalyticsByCustomDateRange() throws Exception {
        mvc.perform(get("/api/admin/analytics")
                .header("Authorization", "Bearer " + adminToken)
                .param("period", "custom")
                .param("fromDate", "2026-10-01")
                .param("toDate", "2026-10-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateRange.period").value("custom"))
                .andExpect(jsonPath("$.dateRange.fromDate").value("2026-10-01"))
                .andExpect(jsonPath("$.dateRange.toDate").value("2026-10-28"))
                .andExpect(jsonPath("$.dailyTrends").isArray());
    }

    @Test
    void analyticsRejectsInvertedDateRange() throws Exception {
        mvc.perform(get("/api/admin/analytics")
                .header("Authorization", "Bearer " + adminToken)
                .param("period", "custom")
                .param("fromDate", "2026-12-31")
                .param("toDate", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthorizedAndCustomerAccessToAnalyticsIsBlocked() throws Exception {
        mvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/analytics")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteProviderWithoutBookings() throws Exception {
        String provReg = """
            {"name":"AdminTest Delete Prov","email":"admin_delete_prov_%d@test.com","phone":"9876543299","password":"password123","role":"PROVIDER","businessName":"Temporary Delete Business"}
            """.formatted(System.currentTimeMillis());
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(provReg))
                .andExpect(status().isCreated());

        String listRes = mvc.perform(get("/api/admin/providers")
                .param("search", "Temporary Delete Business")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number provId = JsonPath.read(listRes, "$[0].provider.id");

        mvc.perform(delete("/api/admin/providers/" + provId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void nonAdminCannotDeleteProvider() throws Exception {
        mvc.perform(delete("/api/admin/providers/999999")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/admin/providers/999999"))
                .andExpect(status().isUnauthorized());
    }
}
