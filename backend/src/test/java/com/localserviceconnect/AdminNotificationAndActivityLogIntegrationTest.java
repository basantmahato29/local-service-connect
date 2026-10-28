package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import com.localserviceconnect.entity.Role;
import com.localserviceconnect.entity.User;
import com.localserviceconnect.repository.AdminActivityLogRepository;
import com.localserviceconnect.repository.NotificationRepository;
import com.localserviceconnect.repository.UserRepository;
import com.localserviceconnect.service.AdminActivityLogService;
import com.localserviceconnect.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNotificationAndActivityLogIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AdminActivityLogService adminActivityLogService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AdminActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private String customerToken;
    private String adminEmail;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "admin@test.com";
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
            {"name":"Audit Cust","email":"audit_cust_%d@test.com","phone":"9876543111","password":"password123","role":"CUSTOMER"}
            """.formatted(System.currentTimeMillis());
        String custRes = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(custReg))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customerToken = JsonPath.read(custRes, "$.token");
    }

    @Test
    void testAdminCanListNotificationsAndFilter() throws Exception {
        // Emit test admin notification
        notificationService.notifyAdmins(
                "NEW_PROVIDER_REGISTRATION",
                "Integration Test Provider Registration",
                "Provider John registered successfully",
                "PROVIDER",
                999L,
                "/admin/providers.html",
                "NORMAL",
                "TEST_REG_" + System.currentTimeMillis()
        );

        // Fetch notifications paged
        mvc.perform(get("/api/admin/notifications?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        // Filter by type
        mvc.perform(get("/api/admin/notifications?type=NEW_PROVIDER_REGISTRATION&page=0&size=5")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));

        // Filter by unread status
        mvc.perform(get("/api/admin/notifications?read=false&page=0&size=5")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()));
    }

    @Test
    void testNotificationUnreadCountAndMarkRead() throws Exception {
        // Get unread count
        mvc.perform(get("/api/admin/notifications/unread-count")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", notNullValue()));

        // Create a specific notification to test read toggling
        String dedup = "TEST_READ_TOGGLE_" + System.currentTimeMillis();
        notificationService.notifyAdmins(
                "TEST_ACTION",
                "Test Action Title",
                "Message for read test",
                "TEST",
                123L,
                "/admin/dashboard.html",
                "HIGH",
                dedup
        );

        // Fetch recent to get notification ID
        String listRes = mvc.perform(get("/api/admin/notifications?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Number notifIdNum = JsonPath.read(listRes, "$.content[0].id");
        Long notifId = notifIdNum.longValue();

        // Mark read
        mvc.perform(put("/api/admin/notifications/" + notifId + "/read")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)));

        // Toggle back to unread
        mvc.perform(put("/api/admin/notifications/" + notifId + "/toggle-read?read=false")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(false)));

        // Mark all as read
        mvc.perform(put("/api/admin/notifications/read-all")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("marked as read")));
    }

    @Test
    void testNotificationCreationOnProviderRegistration() throws Exception {
        long countBefore = notificationRepository.count();

        String provEmail = "test_prov_" + System.currentTimeMillis() + "@example.com";
        String provReg = """
            {"name":"Auto Notif Provider","email":"%s","phone":"9876543999","password":"password123","role":"PROVIDER","businessName":"Auto Clean"}
            """.formatted(provEmail);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(provReg))
                .andExpect(status().isCreated());

        long countAfter = notificationRepository.count();
        assertTrue(countAfter > countBefore, "A notification should be created when a new provider registers");

        // Verify the notification content in database
        boolean found = notificationRepository.findAll().stream()
                .anyMatch(n -> "NEW_PROVIDER_REGISTRATION".equals(n.getType()) &&
                               n.getMessage() != null && n.getMessage().contains("Auto Notif Provider"));
        assertTrue(found, "Should contain NEW_PROVIDER_REGISTRATION notification");
    }

    @Test
    void testNotificationDeduplication() {
        String dedupKey = "DEDUP_TEST_" + System.currentTimeMillis();

        long countBefore = notificationRepository.count();
        notificationService.notifyAdmins(
                "TEST_DEDUP",
                "Dedup Notification 1",
                "First copy",
                "SYSTEM",
                1L,
                "/admin/dashboard.html",
                "NORMAL",
                dedupKey
        );

        long countAfterFirst = notificationRepository.count();
        assertTrue(countAfterFirst > countBefore, "First notification should be recorded");

        // Second notification with identical dedupKey must be discarded
        notificationService.notifyAdmins(
                "TEST_DEDUP",
                "Dedup Notification 2",
                "Duplicate copy",
                "SYSTEM",
                1L,
                "/admin/dashboard.html",
                "NORMAL",
                dedupKey
        );

        long countAfterSecond = notificationRepository.count();
        assertEquals(countAfterFirst, countAfterSecond, "Duplicate notification must not be persisted");
    }

    @Test
    void testAdminActivityLoggingAndRetrieval() throws Exception {
        User adminUser = userRepository.findByEmail(adminEmail).orElseThrow();

        // Record an audit log entry
        adminActivityLogService.record(
                adminUser,
                "PROVIDER_SUSPENDED",
                "PROVIDER",
                888L,
                "Provider John Doe",
                "Suspended provider due to compliance policy check",
                "Policy violation",
                "/admin/providers.html?providerId=888"
        );

        // Fetch activity logs
        mvc.perform(get("/api/admin/activity-logs?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        // Fetch action types and entity types
        mvc.perform(get("/api/admin/activity-logs/action-types")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("PROVIDER_SUSPENDED")));

        mvc.perform(get("/api/admin/activity-logs/entity-types")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("PROVIDER")));
    }

    @Test
    void testActivityLogImmutabilityAndAccessControl() throws Exception {
        // Customer attempting to access admin notifications -> 403
        mvc.perform(get("/api/admin/notifications")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Customer attempting to access admin activity logs -> 403
        mvc.perform(get("/api/admin/activity-logs")
                .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // Unauthenticated access -> 403 or 401
        mvc.perform(get("/api/admin/activity-logs"))
                .andExpect(status().is4xxClientError());

        // Strict audit immutability: No DELETE or PUT endpoint on activity logs exists
        mvc.perform(delete("/api/admin/activity-logs/1")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testQuickServiceAlertEvaluation() {
        // Quick Service alert scanner runs without throwing
        assertDoesNotThrow(() -> notificationService.checkQuickServiceAlerts());
    }
}

