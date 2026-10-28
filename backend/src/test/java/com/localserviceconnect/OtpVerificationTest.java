package com.localserviceconnect;

import com.jayway.jsonpath.JsonPath;
import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.service.BookingService;
import com.localserviceconnect.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OtpVerificationTest {

    static final String CUSTOMER_EMAIL = "otp-customer@test.com";
    static final String PROVIDER_EMAIL = "otp-provider@test.com";
    static final String OTHER_CUSTOMER_EMAIL = "other-cust@test.com";
    static final String OTHER_PROVIDER_EMAIL = "other-prov@test.com";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired ProviderRepository providers;
    @Autowired ServiceTypeRepository serviceTypes;
    @Autowired ProviderAreaRepository areas;
    @Autowired BookingService bookingService;
    @Autowired OtpService otpService;
    @Autowired ServiceOtpRepository otpRepository;
    @Autowired BookingRepository bookingRepository;

    ServiceType electrician;
    Provider provider;
    Customer customer;

    @BeforeEach
    void setUp() {
        ServiceType s = new ServiceType();
        s.setName("Electrician-OTP-Test");
        electrician = serviceTypes.save(s);

        customer = createCustomer(CUSTOMER_EMAIL);
        provider = createProvider(PROVIDER_EMAIL, "Lalpur");
    }

    // 1. Valid OTP starts the service
    @Test
    void validOtpStartsServiceSuccessfully() {
        Long bookingId = createAndAcceptBooking(provider);

        OtpResponse otpResp = otpService.getOtpForCustomer(bookingId, CUSTOMER_EMAIL);
        assertThat(otpResp.otp()).isNotNull().hasSize(4);

        BookingDto started = otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, otpResp.otp());
        assertThat(started.status()).isEqualTo("SERVICE_STARTED");

        // Verify OTP is marked as verified
        ServiceOtp otp = otpRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(otp.isVerified()).isTrue();
        assertThat(otp.getVerifiedAt()).isNotNull();
        assertThat(otp.getEncryptedOtp()).isNull(); // Encrypted plain OTP permanently wiped
    }

    // 2. Incorrect OTP is rejected
    @Test
    void incorrectOtpIsRejectedAndDecrementsRemainingAttempts() {
        Long bookingId = createAndAcceptBooking(provider);

        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, "9999"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Incorrect OTP")
                .hasMessageContaining("4 attempt(s) remaining");

        Booking b = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    // 3. Expired OTP is rejected
    @Test
    void expiredOtpIsRejected() {
        Long bookingId = createAndAcceptBooking(provider);

        ServiceOtp otp = otpRepository.findByBookingId(bookingId).orElseThrow();
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        otpRepository.save(otp);

        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    // 4. Reusing an OTP is rejected
    @Test
    void reusingAnOtpIsRejected() {
        Long bookingId = createAndAcceptBooking(provider);
        OtpResponse otpResp = otpService.getOtpForCustomer(bookingId, CUSTOMER_EMAIL);

        otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, otpResp.otp());

        // Attempting to verify again must be rejected
        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, otpResp.otp()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been started");
    }

    // 5. A different provider cannot verify the booking's OTP
    @Test
    void differentProviderCannotVerifyBookingOtp() {
        createProvider(OTHER_PROVIDER_EMAIL, "Lalpur");
        Long bookingId = createAndAcceptBooking(provider);
        OtpResponse otpResp = otpService.getOtpForCustomer(bookingId, CUSTOMER_EMAIL);

        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, OTHER_PROVIDER_EMAIL, otpResp.otp()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // 6. Another customer cannot access the OTP
    @Test
    void anotherCustomerCannotAccessOtp() {
        createCustomer(OTHER_CUSTOMER_EMAIL);
        Long bookingId = createAndAcceptBooking(provider);

        assertThatThrownBy(() -> otpService.getOtpForCustomer(bookingId, OTHER_CUSTOMER_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    // 7. Cancelled booking cannot verify OTP
    @Test
    void cancelledBookingCannotVerifyOtp() {
        Long bookingId = createAndAcceptBooking(provider);
        OtpResponse otpResp = otpService.getOtpForCustomer(bookingId, CUSTOMER_EMAIL);

        bookingService.cancel(bookingId, CUSTOMER_EMAIL);

        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, otpResp.otp()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot start service for a CANCELLED booking");
    }

    // 8. OTP cannot be used to bypass booking workflow (e.g. pending booking)
    @Test
    void pendingBookingCannotGenerateOrVerifyOtp() {
        BookingDto b = bookingService.create(CUSTOMER_EMAIL, normalReq(provider));
        assertThat(b.status()).isEqualTo("PENDING");

        // Customer cannot generate OTP while pending
        assertThatThrownBy(() -> otpService.generate(b.id(), CUSTOMER_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("after the provider accepts");

        // Provider cannot verify OTP while pending
        assertThatThrownBy(() -> otpService.verifyAndStart(b.id(), PROVIDER_EMAIL, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be accepted");
    }

    // 9. Excessive incorrect attempts are blocked
    @Test
    void excessiveIncorrectAttemptsBlockVerification() {
        Long bookingId = createAndAcceptBooking(provider);

        // 4 failed attempts
        for (int i = 1; i <= 4; i++) {
            final String dummy = "000" + i;
            assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, dummy))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Incorrect OTP");
        }

        // 5th attempt exceeds maximum
        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, "0005"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maximum attempts reached");

        // Further attempt is blocked
        assertThatThrownBy(() -> otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, "0006"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("blocked");
    }

    // 10. Service completion requires customer confirmation
    @Test
    void serviceCompletionRequiresCustomerConfirmation() {
        Long bookingId = createAndAcceptBooking(provider);
        OtpResponse otpResp = otpService.getOtpForCustomer(bookingId, CUSTOMER_EMAIL);
        otpService.verifyAndStart(bookingId, PROVIDER_EMAIL, otpResp.otp());

        // Provider advances to awaiting completion
        bookingService.advance(bookingId, PROVIDER_EMAIL, "AWAITING_COMPLETION");

        // Provider cannot directly mark completed
        assertThatThrownBy(() -> bookingService.advance(bookingId, PROVIDER_EMAIL, "COMPLETED"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("customer confirmation");

        // Another customer cannot confirm completion
        createCustomer(OTHER_CUSTOMER_EMAIL);
        assertThatThrownBy(() -> otpService.confirmCompletion(bookingId, OTHER_CUSTOMER_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        // Authenticated customer confirms completion
        BookingDto done = otpService.confirmCompletion(bookingId, CUSTOMER_EMAIL);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.completedAt()).isNotNull();
    }

    // 11. End-to-end REST API verification through MockMvc
    @Test
    void fullHttpApiLifecycleThroughMockMvc() throws Exception {
        String custToken = loginOrRegister("http-cust@test.com", "CUSTOMER");
        String provToken = loginOrRegister("http-prov@test.com", "PROVIDER");

        // Provider setup
        Provider p = providers.findByUserEmail("http-prov@test.com").orElseThrow();
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.setAvailability(true);
        p.getServices().add(electrician);
        providers.save(p);
        ProviderArea a = new ProviderArea();
        a.setProviderId(p.getId()); a.setArea("Lalpur"); a.setCity("Ranchi");
        areas.save(a);

        // 1. Customer creates booking
        String reqBody = """
            {
              "providerId": %d,
              "serviceId": %d,
              "bookingType": "NORMAL",
              "bookingDate": "%s",
              "bookingTime": "11:00:00",
              "address": "100 Circular Road",
              "area": "Lalpur",
              "notes": "Bring testing gear"
            }
            """.formatted(p.getId(), electrician.getId(), LocalDate.now().plusDays(3));

        String createRes = mvc.perform(post("/api/bookings")
                .header("Authorization", "Bearer " + custToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reqBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long bookingId = ((Number) JsonPath.read(createRes, "$.id")).longValue();

        // 2. Provider accepts booking
        mvc.perform(put("/api/provider/bookings/" + bookingId + "/accept")
                .header("Authorization", "Bearer " + provToken))
                .andExpect(status().isOk());

        // 3. Customer retrieves OTP
        String otpRes = mvc.perform(get("/api/bookings/" + bookingId + "/otp")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otp").isNotEmpty())
                .andExpect(jsonPath("$.verified").value(false))
                .andReturn().getResponse().getContentAsString();
        String plainOtp = JsonPath.read(otpRes, "$.otp");

        // Provider cannot access OTP endpoint
        mvc.perform(get("/api/bookings/" + bookingId + "/otp")
                .header("Authorization", "Bearer " + provToken))
                .andExpect(status().isNotFound());

        // 4. Provider verifies OTP
        mvc.perform(post("/api/provider/bookings/" + bookingId + "/otp/verify")
                .header("Authorization", "Bearer " + provToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + plainOtp + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SERVICE_STARTED"));

        // 5. Customer confirms completion
        mvc.perform(post("/api/bookings/" + bookingId + "/completion/confirm")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // Helper methods
    private Long createAndAcceptBooking(Provider p) {
        BookingDto b = bookingService.create(CUSTOMER_EMAIL, normalReq(p));
        bookingService.accept(b.id(), p.getUser().getEmail());
        return b.id();
    }

    private Customer createCustomer(String email) {
        User u = new User();
        u.setName("Customer " + email); u.setEmail(email); u.setPhone("9999999999");
        u.setPassword("password"); u.setRole(Role.CUSTOMER);
        u = users.save(u);

        Customer c = new Customer();
        c.setUser(u); c.setAddress("10 Lake Road"); c.setArea("Lalpur"); c.setCity("Ranchi");
        return customers.save(c);
    }

    private Provider createProvider(String email, String area) {
        User u = new User();
        u.setName("Provider " + email); u.setEmail(email); u.setPhone("8888888888");
        u.setPassword("password"); u.setRole(Role.PROVIDER);
        u = users.save(u);

        Provider p = new Provider();
        p.setUser(u); p.setBusinessName("Business " + email);
        p.setVerificationStatus(VerificationStatus.VERIFIED);
        p.setAvailability(true);
        p.getServices().add(electrician);
        p = providers.save(p);

        ProviderArea a = new ProviderArea();
        a.setProviderId(p.getId()); a.setArea(area); a.setCity("Ranchi");
        areas.save(a);
        return p;
    }

    private BookingRequest normalReq(Provider p) {
        return new BookingRequest(p.getId(), electrician.getId(), BookingType.NORMAL,
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), "10 Lake Road", "Lalpur", "Notes");
    }

    private String loginOrRegister(String email, String role) throws Exception {
        String body = """
            {"name":"Test User","email":"%s","phone":"9876543210","password":"password123","role":"%s","businessName":"Test Provider"}
            """.formatted(email, role);
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.token");
    }
}
