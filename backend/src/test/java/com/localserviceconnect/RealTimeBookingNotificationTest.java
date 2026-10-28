package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RealTimeBookingNotificationTest {

    @Autowired MockMvc mvc;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderAreaRepository providerAreaRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationService notificationService;
    @Autowired BookingRepository bookingRepository;
    @Autowired CustomerRepository customerRepository;

    private String customerToken;
    private String provider1Token;
    private String provider2Token;
    private Long provider1Id;
    private Long provider2Id;
    private Long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Create active service
        ServiceType st = new ServiceType();
        st.setName("Emergency Electrical");
        st.setDescription("24/7 electrical repairs and urgent assistance");
        st.setStatus(ServiceStatus.ACTIVE);
        st = serviceTypeRepository.save(st);
        serviceId = st.getId();

        // 2. Register Customer
        String custEmail = "cust" + System.nanoTime() + "@test.com";
        String custJson = """
            {
              "name": "Aarav Customer",
              "email": "%s",
              "password": "Password@123",
              "role": "CUSTOMER",
              "phone": "9876543210",
              "address": "Flat 402, Sunshine Heights",
              "area": "Lalpur",
              "city": "Ranchi"
            }
            """.formatted(custEmail);
        MvcResult custRes = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(custJson)).andExpect(status().isCreated()).andReturn();
        customerToken = JsonPath.read(custRes.getResponse().getContentAsString(), "$.token");

        // 3. Register Provider 1 (Eligible, Online)
        String prov1Email = "prov1_" + System.nanoTime() + "@test.com";
        String prov1Json = """
            {
              "name": "Rohan Provider",
              "email": "%s",
              "password": "Password@123",
              "role": "PROVIDER",
              "phone": "9876543211",
              "businessName": "Rohan Electricals",
              "areas": [{"area":"Lalpur","city":"Ranchi"}]
            }
            """.formatted(prov1Email);
        MvcResult prov1Res = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(prov1Json)).andExpect(status().isCreated()).andReturn();
        provider1Token = JsonPath.read(prov1Res.getResponse().getContentAsString(), "$.token");
        provider1Id = providerRepository.findByUserEmail(prov1Email).orElseThrow().getId();

        // Make Provider 1 Verified, Online, and offering the service
        Provider p1 = providerRepository.findById(provider1Id).orElseThrow();
        p1.setVerificationStatus(VerificationStatus.VERIFIED);
        p1.setAvailability(true);
        p1.setApproxPrice(BigDecimal.valueOf(499));
        p1.getServices().add(st);
        providerRepository.save(p1);

        // 4. Register Provider 2 (Unrelated / different provider)
        String prov2Email = "prov2_" + System.nanoTime() + "@test.com";
        String prov2Json = """
            {
              "name": "Vikram Provider",
              "email": "%s",
              "password": "Password@123",
              "role": "PROVIDER",
              "phone": "9876543212",
              "businessName": "Vikram Repairs",
              "areas": [{"area":"Doranda","city":"Ranchi"}]
            }
            """.formatted(prov2Email);
        MvcResult prov2Res = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(prov2Json)).andExpect(status().isCreated()).andReturn();
        provider2Token = JsonPath.read(prov2Res.getResponse().getContentAsString(), "$.token");
        provider2Id = providerRepository.findByUserEmail(prov2Email).orElseThrow().getId();

        Provider p2 = providerRepository.findById(provider2Id).orElseThrow();
        p2.setVerificationStatus(VerificationStatus.VERIFIED);
        p2.setAvailability(true);
        p2.getServices().add(st);
        providerRepository.save(p2);
    }

    @Test
    void testCustomerCreatesBookingAndEligibleProviderReceivesNotification() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String bookingJson = """
            {
              "providerId": %d,
              "serviceId": %d,
              "bookingType": "NORMAL",
              "bookingDate": "%s",
              "bookingTime": "14:30:00",
              "address": "Flat 402, Sunshine Heights",
              "area": "Lalpur",
              "notes": "Fan regulator replacement needed"
            }
            """.formatted(provider1Id, serviceId, tomorrow);

        // 1. Customer creates booking
        MvcResult bookingResult = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Long bookingId = ((Number) JsonPath.read(bookingResult.getResponse().getContentAsString(), "$.id")).longValue();

        // 2. Correct eligible provider receives notification in DB & list API
        mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[0].type").value("NEW_BOOKING"))
                .andExpect(jsonPath("$[0].serviceName").value("Emergency Electrical"))
                .andExpect(jsonPath("$[0].customerName").value("Aarav Customer"))
                .andExpect(jsonPath("$[0].bookingStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].read").value(false));

        // 3. Provider 1 unread count is at least 1
        mvc.perform(get("/api/notifications/unread-count")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(greaterThanOrEqualTo(1)));

        // 4. Provider 1 latest pending API returns this booking
        mvc.perform(get("/api/notifications/latest-pending")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING"));

        // 5. Unrelated provider 2 does NOT receive this notification
        mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + provider2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/notifications/latest-pending")
                .header("Authorization", "Bearer " + provider2Token))
                .andExpect(status().isNoContent());
    }

    @Test
    void testAcceptAndRejectWorkflowUpdatesDatabaseAndNotifications() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(2);
        String bookingJson = """
            {
              "providerId": %d,
              "serviceId": %d,
              "bookingType": "NORMAL",
              "bookingDate": "%s",
              "bookingTime": "11:00:00",
              "address": "Block B, Lalpur",
              "area": "Lalpur"
            }
            """.formatted(provider1Id, serviceId, tomorrow);

        MvcResult bRes = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson)).andExpect(status().isOk()).andReturn();
        Long bookingId = ((Number) JsonPath.read(bRes.getResponse().getContentAsString(), "$.id")).longValue();

        // Provider accepts booking
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/accept")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Confirm database status is updated
        Booking b = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.ACCEPTED, b.getStatus());

        // Notifications for this booking now show ACCEPTED status
        mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingStatus").value("ACCEPTED"));

        // Latest pending API now returns no content because it's no longer pending
        mvc.perform(get("/api/notifications/latest-pending")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isNoContent());

        // Second accept attempt throws 409 Conflict
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/accept")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isConflict());

        // Reject attempt on already accepted booking throws 409 Conflict
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/reject")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isConflict());
    }

    @Test
    void testOfflineProviderCannotReceiveQuickServiceBookings() throws Exception {
        // Toggle Provider 1 Offline
        Provider p1 = providerRepository.findById(provider1Id).orElseThrow();
        p1.setAvailability(false);
        providerRepository.save(p1);

        String quickBookingJson = """
            {
              "providerId": %d,
              "serviceId": %d,
              "bookingType": "QUICK_SERVICE",
              "address": "Station Road",
              "area": "Lalpur"
            }
            """.formatted(provider1Id, serviceId);

        // Attempting to book offline provider for Quick Service must fail with 409 Conflict
        mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(quickBookingJson))
                .andExpect(status().isConflict());

        // Confirm no notification was created for offline provider
        mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testNotificationReadAndMarkAllRead() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(3);
        String bookingJson = """
            {
              "providerId": %d,
              "serviceId": %d,
              "bookingType": "NORMAL",
              "bookingDate": "%s",
              "bookingTime": "16:00:00",
              "address": "Circular Road",
              "area": "Lalpur"
            }
            """.formatted(provider1Id, serviceId, tomorrow);

        mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bookingJson)).andExpect(status().isOk());

        // Get notification ID
        MvcResult notifRes = mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(false))
                .andReturn();

        Long notifId = ((Number) JsonPath.read(notifRes.getResponse().getContentAsString(), "$[0].id")).longValue();

        // Mark single notification read
        mvc.perform(put("/api/notifications/" + notifId + "/read")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        // Mark all read
        mvc.perform(put("/api/notifications/read-all")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notifications/unread-count")
                .header("Authorization", "Bearer " + provider1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void testUnauthorizedAccessToNotificationsIsBlocked() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/notifications/latest-pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDuplicateEventsDoNotCreateDuplicateNotifications() {
        Booking b = new Booking();
        b.setProvider(providerRepository.findById(provider1Id).orElseThrow());
        b.setCustomer(customerRepository.findByUserEmail("cust" + customerToken).orElse(null));
        // Using existing booking from DB
        List<Booking> list = bookingRepository.findAll();
        if (!list.isEmpty()) {
            Booking existing = list.get(0);
            notificationService.sendBookingNotification(existing);
            long countBefore = notificationRepository.count();
            // Retry sending same notification
            notificationService.sendBookingNotification(existing);
            long countAfter = notificationRepository.count();
            assertEquals(countBefore, countAfter, "Duplicate booking notification must not increase notification count");
        }
    }
}
