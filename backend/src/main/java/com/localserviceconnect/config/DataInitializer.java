package com.localserviceconnect.config;

import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final CustomerRepository customerRepository;
    private final ProviderRepository providerRepository;
    private final ProviderAreaRepository providerAreaRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final DemoDataSeeder demoDataSeeder;

    @Value("${app.admin.email:admin@localserviceconnect.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@12345}")
    private String adminPassword;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    public DataInitializer(
            UserRepository userRepository,
            ServiceTypeRepository serviceTypeRepository,
            CustomerRepository customerRepository,
            ProviderRepository providerRepository,
            ProviderAreaRepository providerAreaRepository,
            BookingRepository bookingRepository,
            ReviewRepository reviewRepository,
            PasswordEncoder passwordEncoder,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            DemoDataSeeder demoDataSeeder) {
        this.userRepository = userRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.customerRepository = customerRepository;
        this.providerRepository = providerRepository;
        this.providerAreaRepository = providerAreaRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.demoDataSeeder = demoDataSeeder;
    }

    @Override
    public void run(String... args) {
        migrateSchema();
        cleanupDuplicateAndDemoProviders();
        initAdmin();
        if (seedDemoData) {
            initDemoData();
            if (demoDataSeeder != null) {
                demoDataSeeder.seedComprehensiveDemoData();
            }
        }
        executeSqlQuietly("UPDATE providers SET is_demo = FALSE WHERE is_demo = TRUE");
    }

    private void migrateSchema() {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("ALTER TABLE providers ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE");
            } catch (Exception ex1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE providers ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE");
                } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE providers MODIFY COLUMN profile_image LONGTEXT");
                log.info("Schema migration: providers.profile_image set to LONGTEXT via MODIFY");
            } catch (Exception ex1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE providers ALTER COLUMN profile_image LONGTEXT");
                    log.info("Schema migration: providers.profile_image set to LONGTEXT via ALTER");
                } catch (Exception ex2) {
                    log.debug("providers.profile_image schema alter notice: {}", ex2.getMessage());
                }
            }

            try {
                jdbcTemplate.execute("ALTER TABLE customers MODIFY COLUMN profile_image LONGTEXT");
                log.info("Schema migration: customers.profile_image set to LONGTEXT via MODIFY");
            } catch (Exception ex1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE customers ALTER COLUMN profile_image LONGTEXT");
                    log.info("Schema migration: customers.profile_image set to LONGTEXT via ALTER");
                } catch (Exception ex2) {
                    log.debug("customers.profile_image schema alter notice: {}", ex2.getMessage());
                }
            }

            try {
                jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS sub_service_id BIGINT");
            } catch (Exception ex1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN sub_service_id BIGINT");
                } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS sub_service_name VARCHAR(150)");
            } catch (Exception ex1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN sub_service_name VARCHAR(150)");
                } catch (Exception ignored) {}
            }

            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sub_services (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        service_id BIGINT NOT NULL,
                        name VARCHAR(150) NOT NULL,
                        description TEXT,
                        base_price DECIMAL(10,2),
                        duration VARCHAR(50),
                        image_url VARCHAR(255),
                        enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        display_order INT DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_sub_service_parent FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
                    )
                """);
                log.info("Schema migration: sub_services table verified");
            } catch (Exception ex) {
                log.debug("sub_services table creation notice: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        recipient_id BIGINT NOT NULL,
                        title VARCHAR(150) NOT NULL,
                        message VARCHAR(500) NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        booking_id BIGINT,
                        booking_id_val BIGINT,
                        service_name VARCHAR(100),
                        customer_name VARCHAR(100),
                        location VARCHAR(255),
                        booking_type VARCHAR(30),
                        booking_date DATE,
                        booking_time TIME,
                        price DECIMAL(10,2),
                        booking_status VARCHAR(30),
                        is_read BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_notif_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                """);
                log.info("Schema migration: notifications table verified");
            } catch (Exception ex) {
                log.debug("notifications table creation notice: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(500)");
                log.info("Schema migration: bookings.cancellation_reason verified");
            } catch (Exception ex) {
                log.debug("bookings.cancellation_reason column alter notice: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS booking_status_history (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        booking_id BIGINT NOT NULL,
                        from_status VARCHAR(30),
                        to_status VARCHAR(30) NOT NULL,
                        action VARCHAR(50) NOT NULL,
                        reason VARCHAR(500),
                        actor_id BIGINT,
                        actor_name VARCHAR(150),
                        actor_role VARCHAR(50),
                        timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                log.info("Schema migration: booking_status_history table verified");
            } catch (Exception ex) {
                log.debug("booking_status_history table creation notice: {}", ex.getMessage());
            }

            try {
                jdbcTemplate.execute("UPDATE bookings SET created_at = COALESCE(booking_date, CURRENT_TIMESTAMP) WHERE created_at IS NULL");
                log.info("Schema migration: bookings.created_at backfilled for null records");
            } catch (Exception ex) {
                log.debug("bookings.created_at backfill notice: {}", ex.getMessage());
            }
        }
    }

    private void executeSqlQuietly(String sql) {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception ex) {
                log.debug("executeSqlNotice: {}", ex.getMessage());
            }
        }
    }

    private void cleanupDuplicateAndDemoProviders() {
        if (jdbcTemplate == null) return;
        try {
            // 1. Remove duplicate/test providers
            List<Long> duplicateIds = jdbcTemplate.queryForList("""
                SELECT id FROM providers 
                WHERE business_name LIKE 'Super Electrics %'
                   OR business_name IN (
                       'Prime Electricals', 'Concurrent Services', 'Sharma Electrical Works',
                       'AutoPro Garage & Body Shop', 'Ramesh Electrical Services', 'Apex Motor Care Workshop', 'OTP Electricals'
                   )
            """, Long.class);

            if (!duplicateIds.isEmpty()) {
                log.info("Cleaning up {} duplicate/test provider records...", duplicateIds.size());
                String idInClause = duplicateIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
                
                // Get user IDs
                List<Long> userIds = jdbcTemplate.queryForList(
                    "SELECT user_id FROM providers WHERE id IN (" + idInClause + ")", Long.class);
                String userInClause = userIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));

                // Disable referential integrity temporarily
                executeSqlQuietly("SET REFERENTIAL_INTEGRITY FALSE");
                executeSqlQuietly("SET FOREIGN_KEY_CHECKS = 0");

                try {
                    executeSqlQuietly("DELETE FROM customer_favorites WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM service_otps WHERE booking_id IN (SELECT id FROM bookings WHERE provider_id IN (" + idInClause + "))");
                    if (!userInClause.isEmpty()) {
                        executeSqlQuietly("DELETE FROM notifications WHERE booking_id IN (SELECT id FROM bookings WHERE provider_id IN (" + idInClause + ")) OR recipient_id IN (" + userInClause + ")");
                    } else {
                        executeSqlQuietly("DELETE FROM notifications WHERE booking_id IN (SELECT id FROM bookings WHERE provider_id IN (" + idInClause + "))");
                    }
                    executeSqlQuietly("DELETE FROM reviews WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM bookings WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM provider_services WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM provider_areas WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM provider_sub_services WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM provider_portfolios WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM provider_videos WHERE provider_id IN (" + idInClause + ")");
                    executeSqlQuietly("DELETE FROM verification_documents WHERE application_id IN (SELECT id FROM verification_applications WHERE provider_id IN (" + idInClause + "))");
                    executeSqlQuietly("DELETE FROM verification_applications WHERE provider_id IN (" + idInClause + ")");
                    
                    // Delete provider records
                    executeSqlQuietly("DELETE FROM providers WHERE id IN (" + idInClause + ")");
                    
                    // Delete associated user accounts
                    if (!userIds.isEmpty()) {
                        executeSqlQuietly("DELETE FROM users WHERE id IN (" + userInClause + ")");
                    }
                } finally {
                    // Re-enable referential integrity
                    executeSqlQuietly("SET REFERENTIAL_INTEGRITY TRUE");
                    executeSqlQuietly("SET FOREIGN_KEY_CHECKS = 1");
                }

                log.info("Successfully deleted {} duplicate/test providers and their associated accounts.", duplicateIds.size());
            }

            // 2. Remove [DEMO] and [demo] text from provider business names
            executeSqlQuietly("UPDATE providers SET business_name = TRIM(REPLACE(business_name, '[DEMO] ', '')) WHERE business_name LIKE '%[DEMO]%'");
            executeSqlQuietly("UPDATE providers SET business_name = TRIM(REPLACE(business_name, '[DEMO]', '')) WHERE business_name LIKE '%[DEMO]%'");
            executeSqlQuietly("UPDATE providers SET business_name = TRIM(REPLACE(business_name, '[demo] ', '')) WHERE business_name LIKE '%[demo]%'");
            executeSqlQuietly("UPDATE providers SET business_name = TRIM(REPLACE(business_name, '[demo]', '')) WHERE business_name LIKE '%[demo]%'");
            
            // Set is_demo = false so that they display as regular verified providers everywhere
            executeSqlQuietly("UPDATE providers SET is_demo = FALSE WHERE is_demo = TRUE");

            log.info("Provider profile '[DEMO]' tags cleaned up successfully.");
        } catch (Exception ex) {
            log.warn("Notice during duplicate provider cleanup: {}", ex.getMessage());
        }
    }

    private void initAdmin() {
        String email = adminEmail.trim().toLowerCase();
        if (!userRepository.existsByEmail(email)) {
            User admin = new User();
            admin.setName("System Administrator");
            admin.setEmail(email);
            admin.setPhone("0000000000");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            userRepository.save(admin);
            log.info("Default admin user created with email: {}", email);
        } else {
            log.info("Admin user already exists with email: {}", email);
        }
    }

    private void initDemoData() {
        log.info("Initializing demo data for local environment...");

        // 1. Service types are managed and seeded canonically by DemoDataSeeder

        // 2. Seed Customer
        String custEmail = "customer@localserviceconnect.com";
        Customer customer = null;
        if (!userRepository.existsByEmail(custEmail)) {
            User cu = new User();
            cu.setName("Sunil Verma");
            cu.setEmail(custEmail);
            cu.setPhone("9876543210");
            cu.setPassword(passwordEncoder.encode("Customer@12345"));
            cu.setRole(Role.CUSTOMER);
            cu.setEnabled(true);
            cu = userRepository.save(cu);

            Customer c = new Customer();
            c.setUser(cu);
            c.setAddress("Flat 302, Green Valley Apartments");
            c.setArea("Lalpur");
            c.setCity("Ranchi");
            c.setPincode("834001");
            c.setFormattedAddress("Flat 302, Green Valley Apartments, Lalpur, Ranchi, Jharkhand 834001, India");
            c.setLatitude(23.3712);
            c.setLongitude(85.3361);
            customer = customerRepository.save(c);
            log.info("Seeded demo customer: {}", custEmail);
        } else {
            customer = customerRepository.findByUserEmail(custEmail).orElse(null);
            if (customer != null && customer.getLatitude() == null) {
                customer.setFormattedAddress("Flat 302, Green Valley Apartments, Lalpur, Ranchi, Jharkhand 834001, India");
                customer.setLatitude(23.3712);
                customer.setLongitude(85.3361);
                customerRepository.save(customer);
            }
        }

        // 3. Seed Providers
        String p1Email = "provider@localserviceconnect.com";
        Provider p1 = null;
        if (!userRepository.existsByEmail(p1Email)) {
            User pu = new User();
            pu.setName("Ramesh Sharma");
            pu.setEmail(p1Email);
            pu.setPhone("9876543211");
            pu.setPassword(passwordEncoder.encode("Provider@12345"));
            pu.setRole(Role.PROVIDER);
            pu.setEnabled(true);
            pu = userRepository.save(pu);

            Provider p = new Provider();
            p.setUser(pu);
            p.setBusinessName("Sharma Electricals & Repairs");
            p.setDescription("Certified electrician with 8+ years experience in wiring, fuse repairs, and home electrical fittings.");
            p.setExperienceYears(8);
            p.setApproxPrice(new BigDecimal("350.00"));
            p.setVerificationStatus(VerificationStatus.VERIFIED);
            p.setAvailability(true);
            p.setRating(new BigDecimal("4.9"));
            p.setReviewCount(14);
            p.setFormattedAddress("Lalpur Chowk, Ranchi, Jharkhand 834001, India");
            p.setLatitude(23.3685);
            p.setLongitude(85.3340);
            p.setServiceRadiusKm(10.0);
            p.setProfileImage("https://images.unsplash.com/photo-1540569014015-19a7be504e3a?auto=format&fit=crop&w=400&q=80");
            p.setIsDemo(false);

            serviceTypeRepository.findByName("Electrician").ifPresent(p.getServices()::add);
            serviceTypeRepository.findByName("AC repair and servicing").ifPresent(p.getServices()::add);
            p1 = providerRepository.save(p);

            ProviderArea a1 = new ProviderArea();
            a1.setProviderId(p1.getId());
            a1.setArea("Lalpur");
            a1.setCity("Ranchi");
            providerAreaRepository.save(a1);

            ProviderArea a2 = new ProviderArea();
            a2.setProviderId(p1.getId());
            a2.setArea("Circular Road");
            a2.setCity("Ranchi");
            providerAreaRepository.save(a2);

            log.info("Seeded demo provider 1: {}", p1Email);
        } else {
            p1 = providerRepository.findByUserEmail(p1Email).orElse(null);
            if (p1 != null) {
                if (p1.getLatitude() == null) {
                    p1.setFormattedAddress("Lalpur Chowk, Ranchi, Jharkhand 834001, India");
                    p1.setLatitude(23.3685);
                    p1.setLongitude(85.3340);
                    p1.setServiceRadiusKm(10.0);
                }
                if (p1.getProfileImage() == null) {
                    p1.setProfileImage("https://images.unsplash.com/photo-1540569014015-19a7be504e3a?auto=format&fit=crop&w=400&q=80");
                }
                providerRepository.save(p1);
            }
        }

        String p2Email = "plumber@localserviceconnect.com";
        Provider p2 = null;
        if (!userRepository.existsByEmail(p2Email)) {
            User pu = new User();
            pu.setName("Vikram Singh");
            pu.setEmail(p2Email);
            pu.setPhone("9876543212");
            pu.setPassword(passwordEncoder.encode("Plumber@12345"));
            pu.setRole(Role.PROVIDER);
            pu.setEnabled(true);
            pu = userRepository.save(pu);

            Provider p = new Provider();
            p.setUser(pu);
            p.setBusinessName("Singh Plumbing Solutions");
            p.setDescription("Specialist in bathroom plumbing, tap leakages, pipe installations and emergency water system repairs.");
            p.setExperienceYears(10);
            p.setApproxPrice(new BigDecimal("300.00"));
            p.setVerificationStatus(VerificationStatus.VERIFIED);
            p.setAvailability(true);
            p.setRating(new BigDecimal("5.0"));
            p.setReviewCount(19);
            p.setFormattedAddress("Circular Road, Lalpur, Ranchi, Jharkhand 834001, India");
            p.setLatitude(23.3720);
            p.setLongitude(85.3380);
            p.setServiceRadiusKm(8.0);
            p.setProfileImage("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80");
            p.setIsDemo(false);

            serviceTypeRepository.findByName("Plumbing").ifPresent(p.getServices()::add);
            p2 = providerRepository.save(p);

            ProviderArea a1 = new ProviderArea();
            a1.setProviderId(p2.getId());
            a1.setArea("Lalpur");
            a1.setCity("Ranchi");
            providerAreaRepository.save(a1);

            log.info("Seeded demo provider 2: {}", p2Email);
        } else {
            p2 = providerRepository.findByUserEmail(p2Email).orElse(null);
            if (p2 != null) {
                if (p2.getLatitude() == null) {
                    p2.setFormattedAddress("Circular Road, Lalpur, Ranchi, Jharkhand 834001, India");
                    p2.setLatitude(23.3720);
                    p2.setLongitude(85.3380);
                    p2.setServiceRadiusKm(8.0);
                }
                if (p2.getProfileImage() == null) {
                    p2.setProfileImage("https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80");
                }
                p2.setIsDemo(true);
                providerRepository.save(p2);
            }
        }

        String p3Email = "cleaner@localserviceconnect.com";
        if (!userRepository.existsByEmail(p3Email)) {
            User pu = new User();
            pu.setName("Anita Roy");
            pu.setEmail(p3Email);
            pu.setPhone("9876543213");
            pu.setPassword(passwordEncoder.encode("Cleaner@12345"));
            pu.setRole(Role.PROVIDER);
            pu.setEnabled(true);
            pu = userRepository.save(pu);

            Provider p = new Provider();
            p.setUser(pu);
            p.setBusinessName("Sparkle Deep Clean");
            p.setDescription("Comprehensive home & kitchen deep cleaning, sofa sanitization and surface sterilization.");
            p.setExperienceYears(5);
            p.setApproxPrice(new BigDecimal("499.00"));
            p.setVerificationStatus(VerificationStatus.VERIFIED);
            p.setAvailability(true);
            p.setRating(new BigDecimal("4.8"));
            p.setReviewCount(9);
            p.setFormattedAddress("Kanke Road, Ranchi, Jharkhand 834008, India");
            p.setLatitude(23.4150);
            p.setLongitude(85.3220);
            p.setServiceRadiusKm(15.0);
            p.setProfileImage("https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80");
            p.setIsDemo(true);

            serviceTypeRepository.findByName("Home cleaning").ifPresent(p.getServices()::add);
            Provider savedP3 = providerRepository.save(p);

            ProviderArea a1 = new ProviderArea();
            a1.setProviderId(savedP3.getId());
            a1.setArea("Lalpur");
            a1.setCity("Ranchi");
            providerAreaRepository.save(a1);

            ProviderArea a2 = new ProviderArea();
            a2.setProviderId(savedP3.getId());
            a2.setArea("Kanke");
            a2.setCity("Ranchi");
            providerAreaRepository.save(a2);

            log.info("Seeded demo provider 3: {}", p3Email);
        } else {
            Provider p3 = providerRepository.findByUserEmail(p3Email).orElse(null);
            if (p3 != null) {
                if (p3.getLatitude() == null) {
                    p3.setFormattedAddress("Kanke Road, Ranchi, Jharkhand 834008, India");
                    p3.setLatitude(23.4150);
                    p3.setLongitude(85.3220);
                    p3.setServiceRadiusKm(15.0);
                }
                if (p3.getProfileImage() == null) {
                    p3.setProfileImage("https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80");
                }
                providerRepository.save(p3);
            }
        }

        // 4. Seed Completed Bookings and Reviews (for Featured Reviews on index.html)
        if (bookingRepository.count() == 0 && customer != null && p1 != null) {
            ServiceType st1 = serviceTypeRepository.findByName("Electrician").orElse(null);
            if (st1 != null) {
                Booking b1 = new Booking();
                b1.setCustomer(customer);
                b1.setProvider(p1);
                b1.setService(st1);
                b1.setAddress(customer.getAddress());
                b1.setArea(customer.getArea());
                b1.setBookingType(BookingType.QUICK_SERVICE);
                b1.setStatus(BookingStatus.COMPLETED);
                b1.setNotes("Emergency main fuse trip");
                b1.setCompletedAt(LocalDateTime.now().minusDays(1));
                b1 = bookingRepository.save(b1);

                Review r1 = new Review();
                r1.setBooking(b1);
                r1.setCustomer(customer);
                r1.setProvider(p1);
                r1.setRating(5);
                r1.setReview("Ramesh arrived within 10 minutes for Quick Service and fixed the main fuse immediately. Professional and highly recommended!");
                reviewRepository.save(r1);
            }

            if (p2 != null) {
                ServiceType st2 = serviceTypeRepository.findByName("Plumbing").orElse(null);
                if (st2 != null) {
                    Booking b2 = new Booking();
                    b2.setCustomer(customer);
                    b2.setProvider(p2);
                    b2.setService(st2);
                    b2.setAddress(customer.getAddress());
                    b2.setArea(customer.getArea());
                    b2.setBookingType(BookingType.NORMAL);
                    b2.setBookingDate(LocalDate.now().minusDays(2));
                    b2.setBookingTime(LocalTime.of(11, 0));
                    b2.setStatus(BookingStatus.COMPLETED);
                    b2.setNotes("Kitchen pipe leakage");
                    b2.setCompletedAt(LocalDateTime.now().minusDays(2));
                    b2 = bookingRepository.save(b2);

                    Review r2 = new Review();
                    r2.setBooking(b2);
                    r2.setCustomer(customer);
                    r2.setProvider(p2);
                    r2.setRating(5);
                    r2.setReview("Super fast and clean work. Vikram fixed the kitchen sink leakage with genuine parts.");
                    reviewRepository.save(r2);
                }
            }
            log.info("Seeded demo bookings and featured reviews");
        }
    }

    private ServiceType createService(String name, String description, String icon) {
        ServiceType s = new ServiceType();
        s.setName(name);
        s.setDescription(description);
        s.setIcon(icon);
        s.setStatus(ServiceStatus.ACTIVE);
        return s;
    }
}
