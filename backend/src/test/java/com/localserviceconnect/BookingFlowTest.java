package com.localserviceconnect;

import com.localserviceconnect.dto.Dtos.*;
import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import com.localserviceconnect.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

import static org.assertj.core.api.Assertions.*;

/** Business-rule tests for matching, booking, status flow and reviews. Each test is rolled back. */
@SpringBootTest @AutoConfigureMockMvc @Transactional
class BookingFlowTest {
    static final String CUST = "cust@test.com";
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired ProviderRepository providers;
    @Autowired ServiceTypeRepository serviceTypes;
    @Autowired ProviderAreaRepository areas;
    @Autowired BookingService bookingService;
    @Autowired ReviewService reviewService;
    @Autowired OtpService otpService;

    ServiceType electrician;

    @BeforeEach
    void setUp() {
        ServiceType s = new ServiceType(); s.setName("Electrician");
        electrician = serviceTypes.save(s);
        customer(CUST);
    }

    // ---- matching
    @Test
    void quickMatchReturnsOnlyVerifiedOnlineProvidersInTheArea() {
        Provider ok = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        provider("offline@t.com", VerificationStatus.VERIFIED, false, "Lalpur");
        provider("pending@t.com", VerificationStatus.PENDING, true, "Lalpur");
        provider("faraway@t.com", VerificationStatus.VERIFIED, true, "Kanke");

        assertThat(providers.match(electrician.getId(), "lalpur", true)).extracting(Provider::getId).containsExactly(ok.getId());
        // Normal booking does not need the provider to be online right now
        assertThat(providers.match(electrician.getId(), "lalpur", false)).hasSize(2);
    }

    @Test
    void providerWithAnAcceptedQuickJobIsNoLongerOfferedForQuickService() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        BookingDto b = bookingService.create(CUST, quickReq(p));
        bookingService.accept(b.id(), p.getUser().getEmail());
        assertThat(providers.match(electrician.getId(), "lalpur", true)).isEmpty();
    }

    // ---- booking rules
    @Test
    void quickRequestToAnOfflineProviderIsRefused() {
        Provider p = provider("off@t.com", VerificationStatus.VERIFIED, false, "Lalpur");
        assertThatThrownBy(() -> bookingService.create(CUST, quickReq(p)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no longer available");
    }

    @Test
    void normalBookingCannotBeInThePast() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        BookingRequest past = new BookingRequest(p.getId(), electrician.getId(), BookingType.NORMAL,
                LocalDate.now().minusDays(1), LocalTime.of(10, 0), null, null, null);
        assertThatThrownBy(() -> bookingService.create(CUST, past)).hasMessageContaining("at least 30 minutes");
    }

    @Test
    void normalBookingStartsPending() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, false, "Lalpur");
        BookingDto b = bookingService.create(CUST, normalReq(p));
        assertThat(b.status()).isEqualTo("PENDING");
        assertThat(b.bookingType()).isEqualTo("NORMAL");
    }

    @Test
    void otherCustomersCannotReadABooking() {
        customer("other@t.com");
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        BookingDto b = bookingService.create(CUST, normalReq(p));
        assertThatThrownBy(() -> bookingService.get(b.id(), "other@t.com", "CUSTOMER")).hasMessageContaining("not found");
    }

    // ---- status flow
    @Test
    void statusMovesOneStepAtATimeAndRecordsCompletion() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        String pe = p.getUser().getEmail();
        Long id = bookingService.create(CUST, normalReq(p)).id();
        bookingService.accept(id, pe);

        // Cannot start service without OTP verification
        assertThatThrownBy(() -> bookingService.advance(id, pe, "SERVICE_STARTED"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("OTP verification");

        bookingService.advance(id, pe, "ON_THE_WAY");

        assertThatThrownBy(() -> bookingService.advance(id, pe, "SERVICE_STARTED"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("OTP verification");

        // Customer retrieves OTP and provider verifies it
        OtpResponse otp = otpService.getOtpForCustomer(id, CUST);
        assertThat(otp.otp()).isNotNull().hasSize(4);

        BookingDto started = otpService.verifyAndStart(id, pe, otp.otp());
        assertThat(started.status()).isEqualTo("SERVICE_STARTED");

        // Provider marks service finished
        BookingDto awaiting = bookingService.advance(id, pe, "AWAITING_COMPLETION");
        assertThat(awaiting.status()).isEqualTo("AWAITING_COMPLETION");

        // Provider cannot directly complete
        assertThatThrownBy(() -> bookingService.advance(id, pe, "COMPLETED"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("customer confirmation");

        // Customer confirms completion
        BookingDto done = otpService.confirmCompletion(id, CUST);
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.completedAt()).isNotNull();
    }

    @Test
    void aRejectedRequestCannotBeAcceptedLater() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        Long id = bookingService.create(CUST, normalReq(p)).id();
        bookingService.reject(id, p.getUser().getEmail());
        assertThatThrownBy(() -> bookingService.accept(id, p.getUser().getEmail())).hasMessageContaining("pending");
    }

    // ---- reviews
    @Test
    void reviewsNeedACompletedBookingAndUpdateTheProviderAverage() {
        Provider p = provider("a@t.com", VerificationStatus.VERIFIED, true, "Lalpur");
        String pe = p.getUser().getEmail();
        Long id = bookingService.create(CUST, normalReq(p)).id();
        bookingService.accept(id, pe);

        assertThatThrownBy(() -> reviewService.create(CUST, new ReviewRequest(id, 5, "Great")))
                .hasMessageContaining("completed");

        bookingService.advance(id, pe, "ON_THE_WAY");
        OtpResponse otp = otpService.getOtpForCustomer(id, CUST);
        otpService.verifyAndStart(id, pe, otp.otp());
        bookingService.advance(id, pe, "AWAITING_COMPLETION");
        otpService.confirmCompletion(id, CUST);

        reviewService.create(CUST, new ReviewRequest(id, 4, "Good work"));

        Provider reloaded = providers.findById(p.getId()).orElseThrow();
        assertThat(reloaded.getRating()).isEqualByComparingTo("4.0");
        assertThat(reloaded.getReviewCount()).isEqualTo(1);
        assertThatThrownBy(() -> reviewService.create(CUST, new ReviewRequest(id, 5, null)))
                .hasMessageContaining("already reviewed");
    }

    // ---- helpers
    private User user(String email, Role role) {
        User u = new User();
        u.setName("Test " + email); u.setEmail(email); u.setPhone("9999999999"); u.setPassword("x"); u.setRole(role);
        return users.save(u);
    }
    private void customer(String email) {
        Customer c = new Customer();
        c.setUser(user(email, Role.CUSTOMER)); c.setAddress("12 Main Road"); c.setArea("Lalpur"); c.setCity("Ranchi");
        customers.save(c);
    }
    private Provider provider(String email, VerificationStatus v, boolean online, String area) {
        Provider p = new Provider();
        p.setUser(user(email, Role.PROVIDER)); p.setBusinessName(email); p.setVerificationStatus(v); p.setAvailability(online);
        p.getServices().add(electrician);
        p = providers.save(p);
        ProviderArea a = new ProviderArea();
        a.setProviderId(p.getId()); a.setArea(area); a.setCity("Ranchi");
        areas.save(a);
        return p;
    }
    private BookingRequest quickReq(Provider p) {
        return new BookingRequest(p.getId(), electrician.getId(), BookingType.QUICK_SERVICE, null, null, null, null, null);
    }
    private BookingRequest normalReq(Provider p) {
        return new BookingRequest(p.getId(), electrician.getId(), BookingType.NORMAL,
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), null, null, "Please bring a ladder");
    }
}
