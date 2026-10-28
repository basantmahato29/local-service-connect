package com.localserviceconnect.config;

import com.localserviceconnect.entity.*;
import com.localserviceconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
@Transactional
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final SubServiceRepository catalogSubServiceRepository;
    private final ProviderRepository providerRepository;
    private final ProviderAreaRepository providerAreaRepository;
    private final ProviderSubServiceRepository providerSubServiceRepository;
    private final ProviderPortfolioRepository portfolioRepository;
    private final ProviderVideoRepository videoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataSeeder(
            UserRepository userRepository,
            ServiceTypeRepository serviceTypeRepository,
            SubServiceRepository catalogSubServiceRepository,
            ProviderRepository providerRepository,
            ProviderAreaRepository providerAreaRepository,
            ProviderSubServiceRepository providerSubServiceRepository,
            ProviderPortfolioRepository portfolioRepository,
            ProviderVideoRepository videoRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.catalogSubServiceRepository = catalogSubServiceRepository;
        this.providerRepository = providerRepository;
        this.providerAreaRepository = providerAreaRepository;
        this.providerSubServiceRepository = providerSubServiceRepository;
        this.portfolioRepository = portfolioRepository;
        this.videoRepository = videoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Seeds the canonical 12 Indian service categories, their exact subservices,
     * migrates any legacy/duplicate service rows safely, and seeds demo providers.
     */
    public void seedComprehensiveDemoData() {
        log.info("Starting canonical 12-category home services seeding for Local Service Connect...");
        migrateAndSeedCanonicalCategories();
        seedCatalogSubServices();
        seedDemoProviders();
        log.info("Canonical demo data and subservices seeding completed successfully.");
    }

    private void migrateAndSeedCanonicalCategories() {
        List<CategorySeed> canonicalCategories = List.of(
            new CategorySeed("Cleaning Services", "Professional home deep cleaning, sofa shampooing, kitchen, bathroom and office sanitization", "bi-stars"),
            new CategorySeed("Electrician Services", "Certified electricians for fan repair, wiring, switchboards, MCB, inverter and emergency diagnosis", "bi-lightning-charge"),
            new CategorySeed("Plumbing Services", "Expert plumbers for tap repair, pipe leakages, washbasin, toilet, drain cleaning and water tanks", "bi-droplet"),
            new CategorySeed("AC and Appliance Repair", "Comprehensive repair and servicing for AC, refrigerator, washing machine, microwave, RO and geyser", "bi-snow"),
            new CategorySeed("Salon and Grooming", "Hygienic salon and grooming services at home for men and women, haircuts, styling, facials and bridal", "bi-scissors"),
            new CategorySeed("Vehicle Services", "Doorstep bike and car repair, washing, puncture repair, battery replacement, oil change and inspection", "bi-car-front"),
            new CategorySeed("Laundry and Dry Cleaning", "Doorstep pickup and delivery for clothes washing, steam ironing, dry cleaning, blankets and curtains", "bi-basket"),
            new CategorySeed("Carpenter Services", "Skilled carpenters for furniture repair, door fixing, lock installation, bed and wardrobe assembly", "bi-hammer"),
            new CategorySeed("Painting Services", "Interior and exterior home painting, waterproofing, single room refresh, textures, putty and woodwork polish", "bi-brush"),
            new CategorySeed("Pest Control", "Government certified odorless cockroach gel baiting, termite barrier, bed bug eradication and mosquito control", "bi-shield-shaded"),
            new CategorySeed("Moving and Shifting", "Professional house and office shifting, furniture moving, multi-layer packing and local mini truck transport", "bi-truck"),
            new CategorySeed("Gardening and Home Maintenance", "Plant care, garden maintenance, lawn mowing, repotting, balcony greenery and odd-job home handyman repairs", "bi-tree")
        );

        // 1. Rename single-parent direct equivalents in-place if canonical doesn't exist yet
        Map<String, String> inPlaceRenames = new LinkedHashMap<>();
        inPlaceRenames.put("Electrician", "Electrician Services");
        inPlaceRenames.put("Plumbing", "Plumbing Services");
        inPlaceRenames.put("Home cleaning", "Cleaning Services");
        inPlaceRenames.put("AC repair and servicing", "AC and Appliance Repair");
        inPlaceRenames.put("Salon at home", "Salon and Grooming");
        inPlaceRenames.put("Carpentry", "Carpenter Services");
        inPlaceRenames.put("Painting", "Painting Services");
        inPlaceRenames.put("Pest control", "Pest Control");

        for (Map.Entry<String, String> entry : inPlaceRenames.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            boolean newExists = serviceTypeRepository.existsByName(newName);
            Optional<ServiceType> oldOpt = serviceTypeRepository.findByName(oldName);

            if (oldOpt.isPresent()) {
                if (!newExists) {
                    ServiceType st = oldOpt.get();
                    st.setName(newName);
                    serviceTypeRepository.save(st);
                    log.info("Renamed service in-place: '{}' -> '{}' (preserved ID: {})", oldName, newName, st.getId());
                } else {
                    // Both exist: merge old into new
                    mergeServiceRows(oldOpt.get().getId(), serviceTypeRepository.findByName(newName).get().getId(), oldName, newName);
                }
            }
        }

        // 2. Ensure all 12 canonical parent categories exist in database
        for (CategorySeed cs : canonicalCategories) {
            Optional<ServiceType> existingOpt = serviceTypeRepository.findByName(cs.name);
            if (existingOpt.isEmpty()) {
                ServiceType st = new ServiceType();
                st.setName(cs.name);
                st.setDescription(cs.description);
                st.setIcon(cs.icon);
                st.setStatus(ServiceStatus.ACTIVE);
                serviceTypeRepository.save(st);
                log.info("Created canonical category: {}", cs.name);
            } else {
                ServiceType st = existingOpt.get();
                st.setDescription(cs.description);
                st.setIcon(cs.icon);
                st.setStatus(ServiceStatus.ACTIVE);
                serviceTypeRepository.save(st);
            }
        }

        // 3. Merge legacy sub-category rows into canonical parents
        Map<String, String> mergesToCanonical = new LinkedHashMap<>();
        mergesToCanonical.put("Bathroom cleaning", "Cleaning Services");
        mergesToCanonical.put("Kitchen cleaning", "Cleaning Services");
        mergesToCanonical.put("Appliance repair", "AC and Appliance Repair");
        mergesToCanonical.put("RO and water purifier repair", "AC and Appliance Repair");
        mergesToCanonical.put("Washing machine repair", "AC and Appliance Repair");
        mergesToCanonical.put("Refrigerator repair", "AC and Appliance Repair");
        mergesToCanonical.put("Geyser repair", "AC and Appliance Repair");
        mergesToCanonical.put("Furniture assembly", "Carpenter Services");
        mergesToCanonical.put("CCTV installation", "Electrician Services");
        mergesToCanonical.put("Plumber", "Plumbing Services");
        mergesToCanonical.put("Cleaner", "Cleaning Services");
        mergesToCanonical.put("Barber", "Salon and Grooming");
        mergesToCanonical.put("Carpenter", "Carpenter Services");
        mergesToCanonical.put("AC/Fridge Repair", "AC and Appliance Repair");
        mergesToCanonical.put("Appliance Repair", "AC and Appliance Repair");
        mergesToCanonical.put("Painter", "Painting Services");
        mergesToCanonical.put("Dry Cleaner", "Laundry and Dry Cleaning");
        mergesToCanonical.put("Laundry and dry cleaning", "Laundry and Dry Cleaning");
        mergesToCanonical.put("Mechanic", "Vehicle Services");

        for (Map.Entry<String, String> entry : mergesToCanonical.entrySet()) {
            String legacyName = entry.getKey();
            String canonicalName = entry.getValue();

            Optional<ServiceType> legacyOpt = serviceTypeRepository.findByName(legacyName);
            Optional<ServiceType> canonicalOpt = serviceTypeRepository.findByName(canonicalName);

            if (legacyOpt.isPresent() && canonicalOpt.isPresent()) {
                Long legacyId = legacyOpt.get().getId();
                Long canonicalId = canonicalOpt.get().getId();
                if (!legacyId.equals(canonicalId)) {
                    mergeServiceRows(legacyId, canonicalId, legacyName, canonicalName);
                }
            }
        }

        // Ensure any case variants or lingering duplicate services are merged
        List<ServiceType> currentAll = serviceTypeRepository.findAll();
        for (CategorySeed cs : canonicalCategories) {
            ServiceType canonical = currentAll.stream()
                .filter(s -> s.getName().equals(cs.name))
                .findFirst().orElse(null);
            if (canonical != null) {
                for (ServiceType other : new ArrayList<>(currentAll)) {
                    if (!other.getId().equals(canonical.getId()) && other.getName().equalsIgnoreCase(canonical.getName())) {
                        mergeServiceRows(other.getId(), canonical.getId(), other.getName(), canonical.getName());
                    }
                }
            }
        }
    }

    private void mergeServiceRows(Long legacyId, Long canonicalId, String legacyName, String canonicalName) {
        try {
            // Reassign any bookings pointing to legacyId
            jdbcTemplate.update("UPDATE bookings SET service_id = ? WHERE service_id = ?", canonicalId, legacyId);

            // Reassign catalog sub-services if any were assigned to legacyId
            jdbcTemplate.update("UPDATE sub_services SET service_id = ? WHERE service_id = ?", canonicalId, legacyId);

            // In provider_services: remove rows where provider already has canonicalId
            jdbcTemplate.update("""
                DELETE FROM provider_services
                WHERE service_id = ?
                  AND provider_id IN (
                      SELECT tmp.provider_id FROM (
                          SELECT provider_id FROM provider_services WHERE service_id = ?
                      ) AS tmp
                  )
            """, legacyId, canonicalId);

            // Update remaining provider_services from legacyId to canonicalId
            jdbcTemplate.update("UPDATE provider_services SET service_id = ? WHERE service_id = ?", canonicalId, legacyId);

            // Delete legacy service
            jdbcTemplate.update("DELETE FROM services WHERE id = ?", legacyId);

            log.info("Successfully merged legacy service '{}' (ID: {}) into '{}' (ID: {})",
                    legacyName, legacyId, canonicalName, canonicalId);
        } catch (Exception ex) {
            log.error("Failed to merge legacy service '{}' into '{}': {}", legacyName, canonicalName, ex.getMessage(), ex);
        }
    }

    private void seedCatalogSubServices() {
        Map<String, List<SubServiceSeed>> data = new LinkedHashMap<>();

        // 1. Cleaning Services
        data.put("Cleaning Services", List.of(
            new SubServiceSeed("Sofa Cleaning", "Deep fabric shampooing, vacuuming & stain extraction for fabric & leather sofas", new BigDecimal("699.00"), "60 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Kitchen Deep Cleaning", "Chimney degreasing, slab scrubbing, gas stove cleaning & cabinet wipe", new BigDecimal("799.00"), "90 mins", "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Bathroom Cleaning", "Tile descaling, hard water stain removal, germicidal sanitize & buff", new BigDecimal("499.00"), "45 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Mattress Cleaning", "Industrial allergen extraction, steam sanitization & deep stain removal", new BigDecimal("599.00"), "45 mins", "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Carpet Cleaning", "Rotary shampoo scrubbing, moisture extraction & fiber restoration", new BigDecimal("499.00"), "45 mins", "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Full Home Deep Cleaning", "End-to-end intensive residential scrubbing, floor buffing & sanitization", new BigDecimal("1499.00"), "180 mins", "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Bedroom Cleaning", "Detailed dusting, wardrobe exterior buffing, bed making & floor scrub", new BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Balcony Cleaning", "Pressure floor wash, railing rust clean, drain de-clog & glass wipe", new BigDecimal("349.00"), "30 mins", "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80", 8),
            new SubServiceSeed("Move-in/Move-out Cleaning", "Intensive empty apartment scrub, disinfection & stain removal", new BigDecimal("1299.00"), "150 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80", 9),
            new SubServiceSeed("Office Cleaning", "Workstation sanitization, carpet vacuuming & restroom disinfection", new BigDecimal("999.00"), "120 mins", "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=600&q=80", 10)
        ));

        // 2. Electrician Services
        data.put("Electrician Services", List.of(
            new SubServiceSeed("Fan Installation and Repair", "Ceiling fan, exhaust fan mounting, blade balancing & regulator repair", new BigDecimal("199.00"), "30 mins", "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Switch and Socket Repair", "Modular switchboard replacement, dimmer & power socket repair", new BigDecimal("149.00"), "25 mins", "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Light Installation", "Ceiling LED downlights, chandelier, cove lighting & strip lights", new BigDecimal("249.00"), "35 mins", "https://images.unsplash.com/photo-1517991104123-1d56a6e81ed9?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Wiring and Rewiring", "Concealed conduit wire pulling, short circuit isolation & room rewiring", new BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("MCB and Fuse Repair", "Main distribution box tripping, fuse replacement & phase balancing", new BigDecimal("299.00"), "30 mins", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Inverter Installation and Repair", "Inverter UPS setup, tubular battery wiring & backup troubleshooting", new BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Doorbell Installation", "Wired and smart Wi-Fi video doorbell mounting and chime wiring", new BigDecimal("199.00"), "25 mins", "https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Electrical Fault Diagnosis", "Complete home electrical inspection, earthing test & leak detection", new BigDecimal("299.00"), "40 mins", "https://images.unsplash.com/photo-1621905251918-48416bd8575a?auto=format&fit=crop&w=600&q=80", 8)
        ));

        // 3. Plumbing Services
        data.put("Plumbing Services", List.of(
            new SubServiceSeed("Tap Repair", "Dripping faucet, spindle replacement, aerator cleaning & mixer fix", new BigDecimal("149.00"), "25 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Washbasin Installation", "Countertop/wall-hung washbasin mounting, waste pipe & trap fitting", new BigDecimal("349.00"), "45 mins", "https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Toilet Repair", "Flush cistern valve overhaul, continuous leakage fix & jet spray fitting", new BigDecimal("249.00"), "35 mins", "https://images.unsplash.com/photo-1585058177595-38b8162e08e6?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Water Leakage Repair", "Concealed wall pipe leak detection, CPVC joint sealing & pressure test", new BigDecimal("399.00"), "50 mins", "https://images.unsplash.com/photo-1607472586893-edb57bdc0e39?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Pipe Installation and Repair", "Fresh CPVC/UPVC water line installation, valve replacement & fitting", new BigDecimal("449.00"), "60 mins", "https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Drain Cleaning", "Motorized snaking for clogged kitchen sinks, floor drains & sewer lines", new BigDecimal("299.00"), "40 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Shower Installation", "Overhead rain shower head, hand shower mounting & diverter mixer fix", new BigDecimal("249.00"), "30 mins", "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Water Tank Cleaning", "Overhead water tank de-sludging, scrubbing & UV sanitization", new BigDecimal("599.00"), "75 mins", "https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=600&q=80", 8)
        ));

        // 4. AC and Appliance Repair
        data.put("AC and Appliance Repair", List.of(
            new SubServiceSeed("AC Installation", "Split or window AC indoor/outdoor mounting, piping & vacuum testing", new BigDecimal("799.00"), "90 mins", "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("AC Repair", "Cooling diagnostics, capacitor replacement, PCB repair & noise fix", new BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("AC Servicing", "High-pressure foam jet coil cleaning, filter wash & drain flush", new BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Refrigerator Repair", "Single/Double door cooling issue, relay, thermostat & gas charging", new BigDecimal("399.00"), "50 mins", "https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Washing Machine Repair", "Drum noise, drain pump, spin error, inlet valve & PCB repair", new BigDecimal("349.00"), "45 mins", "https://images.unsplash.com/photo-1626806787461-102c1bfaaea1?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Microwave Repair", "Magnetron failure, touch keypad, heating issue & high voltage diode fix", new BigDecimal("299.00"), "40 mins", "https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("RO Service", "Sediment/carbon filter replacement, RO membrane tuning & TDS check", new BigDecimal("349.00"), "40 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Geyser Repair", "Heating element coil replacement, thermostat reset & leakage fix", new BigDecimal("299.00"), "35 mins", "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80", 8)
        ));

        // 5. Salon and Grooming
        data.put("Salon and Grooming", List.of(
            new SubServiceSeed("Men's Haircut", "Precision haircut, sideburn taper styling & post-cut hair wash/cleanup", new BigDecimal("199.00"), "30 mins", "https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Beard Trimming", "Beard sculpting, fade blend, razor outline & soothing beard oil massage", new BigDecimal("149.00"), "20 mins", "https://images.unsplash.com/photo-1621605815971-fbc98d665033?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Hair Styling", "Professional blowdry, curling, iron straightening & setting spray", new BigDecimal("349.00"), "40 mins", "https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Facial", "O3+ Detan, gold glow herbal facial, deep cleansing & face massage", new BigDecimal("599.00"), "60 mins", "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Hair Colouring", "Root touch-up, global ammonia-free hair colour application & conditioning", new BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Head Massage", "Aromatic oil head massage, pressure points relaxation & shoulder relief", new BigDecimal("249.00"), "30 mins", "https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Women's Haircut", "Trendy layer cut, feather trim, split ends removal & blowdry styling", new BigDecimal("399.00"), "45 mins", "https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Bridal Makeup", "HD party/bridal makeup, eyelashes, saree draping & hairstyling", new BigDecimal("1999.00"), "120 mins", "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=600&q=80", 8)
        ));

        // 6. Vehicle Services
        data.put("Vehicle Services", List.of(
            new SubServiceSeed("Bike Repair", "Two-wheeler engine tune-up, brake shoe replacement, chain adjustment", new BigDecimal("299.00"), "45 mins", "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Car Repair", "Brake pad replacement, suspension check, alternator & engine tune-up", new BigDecimal("599.00"), "60 mins", "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Bike Washing", "High-pressure jet wash, foam wash, chain degrease & polish", new BigDecimal("199.00"), "30 mins", "https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Car Washing", "Exterior snow foam wash, interior vacuuming, tyre dress & glass polish", new BigDecimal("449.00"), "45 mins", "https://images.unsplash.com/photo-1520340356584-f9917d1eea6f?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Puncture Repair", "Tubeless tyre puncture repair, mushroom patch & air pressure check", new BigDecimal("149.00"), "20 mins", "https://images.unsplash.com/photo-1578844251758-2f71da64c96f?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Battery Replacement", "Exide/Amaron battery installation, terminal cleaning & alternator test", new BigDecimal("249.00"), "30 mins", "https://images.unsplash.com/photo-1580273916550-e323be2ae537?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Oil Change", "Castrol/Motul engine oil flush & replacement, oil filter change", new BigDecimal("249.00"), "30 mins", "https://images.unsplash.com/photo-1487754180451-c456f719a1fc?auto=format&fit=crop&w=600&q=80", 7),
            new SubServiceSeed("Vehicle Inspection", "Comprehensive 40-point vehicle pre-purchase inspection & road test", new BigDecimal("499.00"), "45 mins", "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=600&q=80", 8)
        ));

        // 7. Laundry and Dry Cleaning
        data.put("Laundry and Dry Cleaning", List.of(
            new SubServiceSeed("Clothes Washing", "Doorstep wash, sanitize & machine tumble dry for regular daily wear", new BigDecimal("149.00"), "24 hrs", "https://images.unsplash.com/photo-1545173168-9f1947eebb7f?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Ironing", "Heavy steam press on vacuum iron table with crisp crease setting", new BigDecimal("99.00"), "12 hrs", "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Dry Cleaning", "Suit, blazer, tuxedo & designer wear organic solvent dry cleaning", new BigDecimal("249.00"), "48 hrs", "https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Blanket Cleaning", "Heavy winter quilt, comforter & mink blanket deep dry wash", new BigDecimal("299.00"), "48 hrs", "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Curtain Cleaning", "Curtain drape wash, dry clean, pleat press & dust extraction", new BigDecimal("249.00"), "48 hrs", "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Shoe Cleaning", "Sneakers, suede & leather shoe gentle stain wash & deodorization", new BigDecimal("199.00"), "24 hrs", "https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Laundry Pickup and Delivery", "Free doorstep pickup & delivered packed to your home", new BigDecimal("49.00"), "Same day", "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=600&q=80", 7)
        ));

        // 8. Carpenter Services
        data.put("Carpenter Services", List.of(
            new SubServiceSeed("Furniture Repair", "Wobbly wooden chairs, broken dining tables & frame reinforcement", new BigDecimal("249.00"), "40 mins", "https://images.unsplash.com/photo-1538688525198-9b88f6f53126?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Door Repair", "Door gap alignment, bottom rubbing trim, latch fix & hinge lubricate", new BigDecimal("199.00"), "30 mins", "https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Lock Installation", "Godrej mortise lock, deadbolt, digital smart lock & latch mounting", new BigDecimal("299.00"), "45 mins", "https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Bed Assembly", "King/Queen wooden hydraulic bed dismantle or assembling", new BigDecimal("499.00"), "60 mins", "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Wardrobe Repair", "Sliding wardrobe channel realignment, soft-close hinge replacement", new BigDecimal("299.00"), "40 mins", "https://images.unsplash.com/photo-1595428774223-ef52624120d2?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Shelf Installation", "Wall floating shelves, corner brackets, modular display unit mounting", new BigDecimal("249.00"), "35 mins", "https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=600&q=80", 6),
            new SubServiceSeed("Furniture Assembly", "IKEA, Pepperfry flatpack furniture assembly with laser leveling", new BigDecimal("399.00"), "50 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80", 7)
        ));

        // 9. Painting Services
        data.put("Painting Services", List.of(
            new SubServiceSeed("Full Home Painting", "Complete interior Asian Paints Royale / Tractor Emulsion painting", new BigDecimal("4999.00"), "3-5 days", "https://images.unsplash.com/photo-1589939705384-5185137a7f0f?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Single Room Painting", "Single bedroom or living area 2-coat repainting & ceiling touch-up", new BigDecimal("1499.00"), "1 day", "https://images.unsplash.com/photo-1562259949-e8e7689d7828?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Wall Painting", "Accent wall designer color, stencil pattern & smooth roller finish", new BigDecimal("799.00"), "4 hrs", "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Door and Window Painting", "Glossy synthetic enamel paint & wooden lacquer polish", new BigDecimal("499.00"), "3 hrs", "https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Waterproofing", "Terrace, balcony & bathroom wall damp-proof chemical membrane", new BigDecimal("1999.00"), "1 day", "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Wall Putty and Repair", "Crack filling, Birla White putty smoothing & primer coat", new BigDecimal("699.00"), "4 hrs", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80", 6)
        ));

        // 10. Pest Control
        data.put("Pest Control", List.of(
            new SubServiceSeed("Cockroach Control", "Odorless Bayer Maxforce gel baiting & kitchen drain disinfection", new BigDecimal("449.00"), "40 mins", "https://images.unsplash.com/photo-1628177142898-93e36e4e3a50?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Termite Control", "Drill-fill-seal perimeter chemical barrier & wood protection spray", new BigDecimal("999.00"), "90 mins", "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Mosquito Control", "Balcony & indoor thermal fogging, standing water larvicide", new BigDecimal("399.00"), "30 mins", "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Bed Bug Treatment", "Two-stage high heat chemical misting & mattress seam injection", new BigDecimal("799.00"), "60 mins", "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Rat Control", "Rodent bait stations, sticky glue pads & entry point sealing advice", new BigDecimal("399.00"), "35 mins", "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80", 5),
            new SubServiceSeed("Ant Control", "Herbal repellent spray & organic barrier across doors and windows", new BigDecimal("349.00"), "30 mins", "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?auto=format&fit=crop&w=600&q=80", 6)
        ));

        // 11. Moving and Shifting
        data.put("Moving and Shifting", List.of(
            new SubServiceSeed("Home Shifting", "Complete 1/2/3 BHK household packaging, loading & safe transit", new BigDecimal("3499.00"), "4-6 hrs", "https://images.unsplash.com/photo-1600518464441-9154a4dea21b?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Office Shifting", "Desks, computers, filing cabinets & server rack transport", new BigDecimal("4999.00"), "6-8 hrs", "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Furniture Shifting", "Heavy sofa, bed, almirah dismantle, wrap & local mini truck move", new BigDecimal("1299.00"), "2 hrs", "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Packing and Unpacking", "Multi-layer bubble wrap, corrugated boxes & fragile item packing", new BigDecimal("999.00"), "3 hrs", "https://images.unsplash.com/photo-1580674285054-bed31e145f59?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("Local Goods Transport", "Tata Ace / Piaggio Ape tempo with verified driver for local moves", new BigDecimal("699.00"), "1-2 hrs", "https://images.unsplash.com/photo-1519003722824-194d4455a60c?auto=format&fit=crop&w=600&q=80", 5)
        ));

        // 12. Gardening and Home Maintenance
        data.put("Gardening and Home Maintenance", List.of(
            new SubServiceSeed("Plant Care", "Potted plant trimming, organic fertilizing, pest wash & soil aeration", new BigDecimal("299.00"), "45 mins", "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?auto=format&fit=crop&w=600&q=80", 1),
            new SubServiceSeed("Garden Maintenance", "Weeding, hedge shaping, flower bed dressing & overall pruning", new BigDecimal("499.00"), "90 mins", "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=600&q=80", 2),
            new SubServiceSeed("Lawn Mowing", "Lawn trimming using rotary grass mower & edging cleanup", new BigDecimal("399.00"), "60 mins", "https://images.unsplash.com/photo-1592417817098-8f3d69104a47?auto=format&fit=crop&w=600&q=80", 3),
            new SubServiceSeed("Plant Repotting", "Clay/ceramic pot change, enriched potting mix & root pruning", new BigDecimal("249.00"), "30 mins", "https://images.unsplash.com/photo-1485955900006-10f4d324d411?auto=format&fit=crop&w=600&q=80", 4),
            new SubServiceSeed("General Home Maintenance", "Handyman punch list: curtain rods, mirrors, bolts & odd jobs", new BigDecimal("349.00"), "60 mins", "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80", 5)
        ));

        int totalSeeded = 0;
        for (Map.Entry<String, List<SubServiceSeed>> entry : data.entrySet()) {
            String categoryName = entry.getKey();
            Optional<ServiceType> serviceOpt = serviceTypeRepository.findByName(categoryName);
            if (serviceOpt.isEmpty()) {
                continue;
            }
            ServiceType service = serviceOpt.get();
            List<SubService> existingList = catalogSubServiceRepository.findByServiceIdOrderByDisplayOrderAscNameAsc(service.getId());

            for (SubServiceSeed sss : entry.getValue()) {
                SubService sub = existingList.stream()
                        .filter(e -> e.getName().equalsIgnoreCase(sss.name))
                        .findFirst()
                        .orElse(null);

                if (sub == null) {
                    sub = new SubService();
                    sub.setService(service);
                    sub.setName(sss.name);
                    sub.setDescription(sss.description);
                    sub.setBasePrice(sss.price);
                    sub.setDuration(sss.duration);
                    sub.setImageUrl(sss.imageUrl);
                    sub.setEnabled(true);
                    sub.setDisplayOrder(sss.displayOrder);
                    catalogSubServiceRepository.save(sub);
                    totalSeeded++;
                } else {
                    sub.setDescription(sss.description);
                    sub.setBasePrice(sss.price);
                    sub.setDuration(sss.duration);
                    sub.setImageUrl(sss.imageUrl);
                    sub.setEnabled(true);
                    sub.setDisplayOrder(sss.displayOrder);
                    catalogSubServiceRepository.save(sub);
                }
            }
        }
        log.info("Catalog subservices verified/seeded. Total new subservices added: {}", totalSeeded);
    }

    private void seedDemoProviders() {
        List<ProviderDefinition> defs = List.of(
            // 1. Electrician Services Providers
            new ProviderDefinition("demo_prov_elec_1@localserviceconnect.com", "Rajesh Sharma",
                "Sharma Electricals & Inverter Works",
                "Certified senior electrician with 10+ years experience in domestic wiring, modular switchboards, MCB distribution, inverter wiring and emergency short circuit troubleshooting.",
                10, new BigDecimal("249.00"), new BigDecimal("4.9"), 142,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?auto=format&fit=crop&w=400&q=80",
                List.of("Electrician Services"),
                "Fan Installation and Repair", "MCB and Fuse Repair", "Inverter Installation and Repair"),

            new ProviderDefinition("demo_prov_elec_2@localserviceconnect.com", "Manoj Gupta",
                "Maa Tara Electric & Light Solutions",
                "Specialist in ceiling fans, designer LED chandeliers, concealed cove lighting, smart doorbell installation and complete room electrical wiring.",
                8, new BigDecimal("199.00"), new BigDecimal("4.8"), 98,
                "Morabadi Ground Road, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 10.0,
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80",
                List.of("Electrician Services"),
                "Switch and Socket Repair", "Light Installation", "Doorbell Installation"),

            new ProviderDefinition("demo_prov_elec_3@localserviceconnect.com", "Amit Kumar",
                "Chotanagpur Safe Electricals",
                "Prompt, reliable doorstep electrical services covering domestic 3-phase balancing, earth-leakage breakers and heavy appliance power point wiring.",
                6, new BigDecimal("299.00"), new BigDecimal("4.7"), 76,
                "Harmu Housing Colony, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 10.0,
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
                List.of("Electrician Services"),
                "Wiring and Rewiring", "MCB and Fuse Repair", "Electrical Fault Diagnosis"),

            new ProviderDefinition("demo_prov_cctv_1@localserviceconnect.com", "Sarita Bhengra",
                "VisionSafe Electric & Wiring Pros",
                "Authorized electrical contractor and lighting installer. Fast fault diagnosis, concealed conduit wiring, safety breakers and switchboards.",
                9, new BigDecimal("299.00"), new BigDecimal("4.9"), 147,
                "Morabadi, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 13.0,
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80",
                List.of("Electrician Services"),
                "Electrical Fault Diagnosis", "Wiring and Rewiring", "Light Installation"),

            new ProviderDefinition("demo_prov_cctv_2@localserviceconnect.com", "Suman Ghosh",
                "SmartPower Doorbell & Fan Repairs",
                "Neat PVC conduit casing cable routing, smart doorbell chime setup, ceiling fan installation and inverter backup wiring.",
                7, new BigDecimal("249.00"), new BigDecimal("4.8"), 92,
                "Lalpur, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 11.0,
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80",
                List.of("Electrician Services"),
                "Doorbell Installation", "Fan Installation and Repair", "Inverter Installation and Repair"),

            // 2. Plumbing Services Providers
            new ProviderDefinition("demo_prov_plumb_1@localserviceconnect.com", "Vikram Singh",
                "Singh Plumbing & Pipe Leak Solutions",
                "Licensed plumber with 12+ years experience in high-rise and residential pipe leakage, hot/cold shower mixers, flush cistern overhauls and water pressure motors.",
                12, new BigDecimal("249.00"), new BigDecimal("4.9"), 165,
                "Circular Road, Lalpur, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3720, 85.3380, 12.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
                List.of("Plumbing Services"),
                "Tap Repair", "Washbasin Installation", "Toilet Repair"),

            new ProviderDefinition("demo_prov_plumb_2@localserviceconnect.com", "Deepak Das",
                "Ranchi Aqua & Pipe Experts",
                "Specialized motorized snaking equipment for blocked kitchen traps, main bathroom sewer lines, rooftop overhead tank cleaning and CPVC pipe joint repairs.",
                9, new BigDecimal("199.00"), new BigDecimal("4.8"), 112,
                "Doranda Main Road, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 11.0,
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
                List.of("Plumbing Services"),
                "Drain Cleaning", "Water Leakage Repair", "Water Tank Cleaning"),

            new ProviderDefinition("demo_prov_plumb_3@localserviceconnect.com", "Rahul Soren",
                "Ganga Plumbing & Sanitary Care",
                "Affordable and guaranteed repairs for dripping faucets, low water pressure, toilet seat fitting, health faucets and water booster pump maintenance.",
                5, new BigDecimal("149.00"), new BigDecimal("4.7"), 64,
                "Bariatu Road, Near RIMS, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 10.0,
                "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=400&q=80",
                List.of("Plumbing Services"),
                "Tap Repair", "Shower Installation", "Pipe Installation and Repair"),

            // 3. Cleaning Services Providers
            new ProviderDefinition("demo_prov_clean_1@localserviceconnect.com", "Anita Roy",
                "Ranchi Sparkle Deep Cleaning",
                "Premier full home sanitization squad equipped with industrial single-disc floor scrubbers, Karcher vacuum extractors and German eco-friendly cleaning agents.",
                7, new BigDecimal("799.00"), new BigDecimal("4.9"), 145,
                "Kanke Road, Ranchi, Jharkhand 834006, India", "Kanke", 23.4150, 85.3220, 15.0,
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80",
                List.of("Cleaning Services"),
                "Sofa Cleaning", "Kitchen Deep Cleaning", "Bathroom Cleaning"),

            new ProviderDefinition("demo_prov_clean_2@localserviceconnect.com", "Sunita Devi",
                "Jharkhand Home Clean & Sanitization",
                "Detailed residential cleaning covering upholstery shampooing, mattress allergen extraction, window glass buffing and dust-free move-in cleaning.",
                5, new BigDecimal("599.00"), new BigDecimal("4.8"), 118,
                "Harmu Housing Colony, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 12.0,
                "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80",
                List.of("Cleaning Services"),
                "Mattress Cleaning", "Move-in/Move-out Cleaning", "Balcony Cleaning"),

            new ProviderDefinition("demo_prov_clean_3@localserviceconnect.com", "Pooja Kumari",
                "EcoShine Household Services",
                "Trained female cleaning professionals providing detailed dusting, dining table buffing, floor mop descaling and kid-safe fragrance sterilization.",
                4, new BigDecimal("499.00"), new BigDecimal("4.7"), 84,
                "Morabadi, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 10.0,
                "https://images.unsplash.com/photo-1567532939604-b6b5b0db2604?auto=format&fit=crop&w=400&q=80",
                List.of("Cleaning Services"),
                "Bedroom Cleaning", "Carpet Cleaning", "Full Home Deep Cleaning"),

            new ProviderDefinition("demo_prov_bath_1@localserviceconnect.com", "Meena Ekka",
                "Spotless Bathroom & Floor Descaling Clinic",
                "Hard water stain specialist using specialized rotary scrubbers, acid-free tile descalers, glass shower partition buffing and 99.9% toilet sanitization.",
                7, new BigDecimal("349.00"), new BigDecimal("4.9"), 144,
                "Harmu, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 12.0,
                "https://images.unsplash.com/photo-1567532939604-b6b5b0db2604?auto=format&fit=crop&w=400&q=80",
                List.of("Cleaning Services"),
                "Bathroom Cleaning", "Office Cleaning", "Full Home Deep Cleaning"),

            new ProviderDefinition("demo_prov_kitchen_1@localserviceconnect.com", "Jyoti Agarwal",
                "MasterChef Kitchen Deep Cleaners",
                "Heavy kitchen degreasing squad. Complete chimney baffle filter dip wash, gas stove carbon removal, tile grease scrub and cabinet interior wipe.",
                8, new BigDecimal("599.00"), new BigDecimal("4.9"), 153,
                "Morabadi, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 12.0,
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=400&q=80",
                List.of("Cleaning Services"),
                "Kitchen Deep Cleaning", "Sofa Cleaning", "Move-in/Move-out Cleaning"),

            // 4. AC and Appliance Repair Providers
            new ProviderDefinition("demo_prov_ac_1@localserviceconnect.com", "Sunil Oraon",
                "Cool Breeze AC Clinic & Servicing",
                "Certified HVAC technician with expertise in high-pressure foam jet coil deep cleaning, refrigerant leak test, genuine R32/R410A gas charging and PCB repairs.",
                11, new BigDecimal("499.00"), new BigDecimal("4.9"), 188,
                "Hinoo Main Road, Ranchi, Jharkhand 834050, India", "Hinoo", 23.3280, 85.3150, 14.0,
                "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?auto=format&fit=crop&w=400&q=80",
                List.of("AC and Appliance Repair"),
                "AC Installation", "AC Repair", "AC Servicing"),

            new ProviderDefinition("demo_prov_fridge_1@localserviceconnect.com", "Naresh Chawla",
                "ColdTech Refrigerator & Appliance Solutions",
                "Certified refrigeration expert for single door, double door, and side-by-side refrigerators. Gas charging, PTC starter relay and compressor overhaul.",
                13, new BigDecimal("399.00"), new BigDecimal("4.9"), 162,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 13.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
                List.of("AC and Appliance Repair"),
                "Refrigerator Repair", "AC Servicing", "Microwave Repair"),

            new ProviderDefinition("demo_prov_wash_1@localserviceconnect.com", "Brijesh Dubey",
                "Ranchi WashCare Machine Repairs",
                "Top-rated specialist in front load and top load washing machines (Samsung, LG, Whirlpool, Bosch). Drum bearing, shock absorbers and drain pump repair.",
                11, new BigDecimal("349.00"), new BigDecimal("4.9"), 141,
                "Bariatu Road, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 12.0,
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
                List.of("AC and Appliance Repair"),
                "Washing Machine Repair", "Microwave Repair", "Geyser Repair"),

            new ProviderDefinition("demo_prov_ro_1@localserviceconnect.com", "Ravi Shankar",
                "PureDrop RO & Purifier Experts",
                "Specialist in Kent, Aquaguard, Pureit, Livpure purifiers. Complete 3-stage filter replacement, 80 GPD membrane change and digital TDS calibration.",
                9, new BigDecimal("399.00"), new BigDecimal("4.9"), 152,
                "Harmu Housing Colony, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 12.0,
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?auto=format&fit=crop&w=400&q=80",
                List.of("AC and Appliance Repair"),
                "RO Service", "Geyser Repair", "Washing Machine Repair"),

            new ProviderDefinition("demo_prov_geyser_1@localserviceconnect.com", "Archana Singh",
                "HotWater Geyser Repair & Install",
                "Specialist in instant and storage water geysers (AO Smith, Havells, Racold, Bajaj). Heavy duty copper heating coil change and thermostat trip reset.",
                9, new BigDecimal("299.00"), new BigDecimal("4.9"), 129,
                "Morabadi, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 12.0,
                "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80",
                List.of("AC and Appliance Repair"),
                "Geyser Repair", "Microwave Repair", "RO Service"),

            // 5. Salon and Grooming Providers
            new ProviderDefinition("demo_prov_salon_1@localserviceconnect.com", "Priya Verma",
                "Glamour At Home Beauty & Salon",
                "Certified beautician with 9 years salon experience offering sterile single-use waxing kits, charcoal detox facials, O3+ detan, and bridal skin glow therapy.",
                9, new BigDecimal("499.00"), new BigDecimal("4.9"), 156,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1573497019940-1c28c88b4f3e?auto=format&fit=crop&w=400&q=80",
                List.of("Salon and Grooming"),
                "Facial", "Hair Styling", "Bridal Makeup"),

            new ProviderDefinition("demo_prov_salon_2@localserviceconnect.com", "Anand Jha",
                "Urban Cuts & Grooming Lounge",
                "Premium doorstep men's grooming specialist. Single-use sterile razor blades, fade haircuts, beard shaping, head acupressure massage and post-cut vacuum cleanup.",
                8, new BigDecimal("249.00"), new BigDecimal("4.9"), 174,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 11.0,
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
                List.of("Salon and Grooming"),
                "Men's Haircut", "Beard Trimming", "Head Massage"),

            new ProviderDefinition("demo_prov_salon_3@localserviceconnect.com", "Neha Sen",
                "Herbal Touch Home Salon",
                "Gentle herbal treatments including customized hair styling, layer cut, manicures, waxing and herbal face masks using 100% skin-safe organic products.",
                6, new BigDecimal("399.00"), new BigDecimal("4.8"), 93,
                "Bariatu, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 10.0,
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=400&q=80",
                List.of("Salon and Grooming"),
                "Women's Haircut", "Hair Colouring", "Facial"),

            // 6. Vehicle Services Providers
            new ProviderDefinition("demo_prov_veh_1@localserviceconnect.com", "Ajay Verma",
                "Ranchi AutoCare & Bike Clinic",
                "Certified two-wheeler & car mechanic for doorstep oil change, spark plug tuning, brake pad replacements, puncture fixing and engine health audit.",
                9, new BigDecimal("299.00"), new BigDecimal("4.9"), 138,
                "Harmu Housing Colony, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 13.0,
                "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=400&q=80",
                List.of("Vehicle Services"),
                "Bike Repair", "Puncture Repair", "Oil Change"),

            new ProviderDefinition("demo_prov_veh_2@localserviceconnect.com", "Ramesh Yadav",
                "SpeedWheels Car Service & Foam Wash",
                "Doorstep car foam wash, interior vacuuming, battery jump start, Exide battery installation, brake pad servicing and vehicle pre-purchase inspection.",
                8, new BigDecimal("449.00"), new BigDecimal("4.8"), 114,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=400&q=80",
                List.of("Vehicle Services"),
                "Car Repair", "Car Washing", "Battery Replacement"),

            new ProviderDefinition("demo_prov_veh_3@localserviceconnect.com", "Vikas Mahto",
                "Express Doorstep Puncture & Wash",
                "Rapid mobile bike and car puncture service with tubeless mushroom plugs, pressure jet foam wash, chain lube and 40-point vehicle inspection.",
                5, new BigDecimal("199.00"), new BigDecimal("4.7"), 72,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 10.0,
                "https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=400&q=80",
                List.of("Vehicle Services"),
                "Bike Washing", "Puncture Repair", "Vehicle Inspection"),

            // 7. Laundry and Dry Cleaning Providers
            new ProviderDefinition("demo_prov_laundry_1@localserviceconnect.com", "Ritu Bhattacharya",
                "Express Wash Laundry & Dry Clean",
                "Doorstep pickup & delivery dry cleaning for suits, designer sarees, heavy blankets, quilts and curtains with eco-solvent stain extraction.",
                8, new BigDecimal("249.00"), new BigDecimal("4.9"), 135,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=400&q=80",
                List.of("Laundry and Dry Cleaning"),
                "Dry Cleaning", "Blanket Cleaning", "Laundry Pickup and Delivery"),

            new ProviderDefinition("demo_prov_laundry_2@localserviceconnect.com", "Swati Banerjee",
                "Royal Fabrics Dry Cleaners",
                "Delicate fabric handling, steam ironing on vacuum press boards, curtain dry cleaning with pleat retention and moth-proof garment packaging.",
                6, new BigDecimal("199.00"), new BigDecimal("4.8"), 92,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 11.0,
                "https://images.unsplash.com/photo-1580489944761-15a19d654956?auto=format&fit=crop&w=400&q=80",
                List.of("Laundry and Dry Cleaning"),
                "Clothes Washing", "Ironing", "Curtain Cleaning"),

            new ProviderDefinition("demo_prov_laundry_3@localserviceconnect.com", "Tanushree Bose",
                "Pristine Doorstep Laundry Care",
                "Convenient daily doorstep wash, tumble dry and crisp steam press for working professionals and families in Ranchi with same-day express return.",
                4, new BigDecimal("149.00"), new BigDecimal("4.7"), 64,
                "Kanke Road, Ranchi, Jharkhand 834006, India", "Kanke", 23.4150, 85.3220, 10.0,
                "https://images.unsplash.com/photo-1573497019940-1c28c88b4f3e?auto=format&fit=crop&w=400&q=80",
                List.of("Laundry and Dry Cleaning"),
                "Clothes Washing", "Shoe Cleaning", "Ironing"),

            // 8. Carpenter Services Providers
            new ProviderDefinition("demo_prov_carp_1@localserviceconnect.com", "Suraj Karmakar",
                "Chotanagpur Woodcraft & Carpentry",
                "Master carpenter with 14 years experience in Godrej lock installation, hydraulic bed repair, sliding wardrobe channel tuning and teak wood polishing.",
                14, new BigDecimal("299.00"), new BigDecimal("4.9"), 148,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 12.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
                List.of("Carpenter Services"),
                "Furniture Repair", "Lock Installation", "Bed Assembly"),

            new ProviderDefinition("demo_prov_carp_2@localserviceconnect.com", "Binod Mahato",
                "Maa Durga Wood & Furniture Works",
                "Expert in wooden table repair, wobbly chair reinforcement, modular shelf mounting, curtain rod drill work and wooden partition fabrication.",
                9, new BigDecimal("199.00"), new BigDecimal("4.8"), 105,
                "Lalpur, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 11.0,
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
                List.of("Carpenter Services"),
                "Door Repair", "Shelf Installation", "Furniture Assembly"),

            new ProviderDefinition("demo_prov_carp_3@localserviceconnect.com", "Gautam Bedia",
                "UrbanWood Custom Carpentry",
                "Punctual carpenter for quick household fixes: door gap alignment, window latch repair, plywood touch-ups and cabinet magnetic catchers.",
                5, new BigDecimal("149.00"), new BigDecimal("4.7"), 58,
                "Bariatu, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 10.0,
                "https://images.unsplash.com/photo-1540569014015-19a7be504e3a?auto=format&fit=crop&w=400&q=80",
                List.of("Carpenter Services"),
                "Wardrobe Repair", "Door Repair", "Furniture Repair"),

            // 9. Painting Services Providers
            new ProviderDefinition("demo_prov_paint_1@localserviceconnect.com", "Rohit Pandey",
                "Rainbow Colors Wall Painting",
                "Complete interior and exterior painting services using Asian Paints Royale, tractor emulsion, wall putty skimming and waterproofing damp treatment.",
                12, new BigDecimal("1299.00"), new BigDecimal("4.9"), 115,
                "Bariatu Road, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 14.0,
                "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
                List.of("Painting Services"),
                "Full Home Painting", "Waterproofing", "Single Room Painting"),

            new ProviderDefinition("demo_prov_paint_2@localserviceconnect.com", "Arvind Sahu",
                "Ranchi Asian Paint Pro Service",
                "Specialized in metallic texture accent walls, geometric stencils, wood enamel door polishing and post-paint floor cleanup with zero paint stains.",
                8, new BigDecimal("899.00"), new BigDecimal("4.8"), 87,
                "Lalpur, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=400&q=80",
                List.of("Painting Services"),
                "Wall Painting", "Door and Window Painting", "Wall Putty and Repair"),

            new ProviderDefinition("demo_prov_paint_3@localserviceconnect.com", "Dharmendra Singh",
                "Classic Touch Decor & Painting",
                "Affordable touch-up painting, rental move-out repainting, ceiling water leakage stain cover and exterior weather-coat application.",
                6, new BigDecimal("699.00"), new BigDecimal("4.7"), 62,
                "Harmu, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 10.0,
                "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=400&q=80",
                List.of("Painting Services"),
                "Single Room Painting", "Wall Putty and Repair", "Waterproofing"),

            // 10. Pest Control Providers
            new ProviderDefinition("demo_prov_pest_1@localserviceconnect.com", "Santosh Yadav",
                "SafeHome Pest Control & Shield",
                "Government licensed pest control technician. Odorless cockroach gel baiting, pre & post-construction termite drilling and complete bed bug eradication.",
                10, new BigDecimal("499.00"), new BigDecimal("4.9"), 138,
                "Morabadi, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 13.0,
                "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=400&q=80",
                List.of("Pest Control"),
                "Cockroach Control", "Termite Control", "Bed Bug Treatment"),

            new ProviderDefinition("demo_prov_pest_2@localserviceconnect.com", "Mukesh Gope",
                "BugBusters Ranchi Pest Treatment",
                "Balcony mosquito fogging, drain larvicide treatment, rodent bait stations and wood borer treatment with 100% pet-safe eco solutions.",
                7, new BigDecimal("399.00"), new BigDecimal("4.8"), 89,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 11.0,
                "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=400&q=80",
                List.of("Pest Control"),
                "Mosquito Control", "Rat Control", "Ant Control"),

            new ProviderDefinition("demo_prov_pest_3@localserviceconnect.com", "Jitendra Bhagat",
                "Herbal Pest Control Solutions",
                "Non-toxic herbal spray for small apartments, ant elimination, kitchen drawer sanitization and annual pest maintenance plans.",
                5, new BigDecimal("349.00"), new BigDecimal("4.7"), 57,
                "Kanke Road, Ranchi, Jharkhand 834006, India", "Kanke", 23.4150, 85.3220, 10.0,
                "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=400&q=80",
                List.of("Pest Control"),
                "Ant Control", "Cockroach Control", "Mosquito Control"),

            // 11. Moving and Shifting Providers
            new ProviderDefinition("demo_prov_mov_1@localserviceconnect.com", "Dharmendra Prasad",
                "Jharkhand Movers & Packers",
                "Licensed movers and packers in Ranchi. Multi-layer bubble packaging, dedicated closed container mini trucks, furniture dismantle and safe room reassembly.",
                11, new BigDecimal("3499.00"), new BigDecimal("4.9"), 143,
                "Harmu Housing Colony, Ranchi, Jharkhand 834002, India", "Harmu", 23.3520, 85.3100, 15.0,
                "https://images.unsplash.com/photo-1600518464441-9154a4dea21b?auto=format&fit=crop&w=400&q=80",
                List.of("Moving and Shifting"),
                "Home Shifting", "Packing and Unpacking", "Furniture Shifting"),

            new ProviderDefinition("demo_prov_mov_2@localserviceconnect.com", "Sunil Gope",
                "Ranchi Cargo & Mini Truck Shifting",
                "Quick local mini truck transport (Tata Ace / Mahindra Bolero Maxi Truck), commercial office desks moving, sofa/almirah shifting and fragile item wrapping.",
                8, new BigDecimal("1299.00"), new BigDecimal("4.8"), 96,
                "Lalpur Chowk, Ranchi, Jharkhand 834001, India", "Lalpur", 23.3685, 85.3340, 12.0,
                "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=400&q=80",
                List.of("Moving and Shifting"),
                "Local Goods Transport", "Furniture Shifting", "Office Shifting"),

            new ProviderDefinition("demo_prov_mov_3@localserviceconnect.com", "Sanjay Karmali",
                "Swift Shift Home Packers",
                "Economical packing and moving squad for 1BHK/2BHK flats, student hostel luggage shifting and guaranteed scratch-proof electronics transportation.",
                5, new BigDecimal("999.00"), new BigDecimal("4.7"), 61,
                "Doranda, Ranchi, Jharkhand 834002, India", "Doranda", 23.3350, 85.3200, 10.0,
                "https://images.unsplash.com/photo-1580674285054-bed31e145f59?auto=format&fit=crop&w=400&q=80",
                List.of("Moving and Shifting"),
                "Packing and Unpacking", "Home Shifting", "Local Goods Transport"),

            // 12. Gardening and Home Maintenance Providers
            new ProviderDefinition("demo_prov_gard_1@localserviceconnect.com", "Ramu Mahto",
                "GreenThumb Gardeners & Plant Nursery",
                "Experienced master gardener for potted plant care, balcony kitchen garden setups, pest spraying with neem oil, organic fertilizer and plant repotting.",
                10, new BigDecimal("299.00"), new BigDecimal("4.9"), 127,
                "Morabadi Ground Road, Ranchi, Jharkhand 834008, India", "Morabadi", 23.3900, 85.3350, 12.0,
                "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?auto=format&fit=crop&w=400&q=80",
                List.of("Gardening and Home Maintenance"),
                "Plant Care", "Plant Repotting", "Garden Maintenance"),

            new ProviderDefinition("demo_prov_gard_2@localserviceconnect.com", "Birsa Oraon",
                "NatureCare Lawn & Garden Maintenance",
                "Full lawn mowing, mechanical grass edge trimming, hedge sculpting, dead leaves cleanup and seasonal flower bed planting for Ranchi homes.",
                7, new BigDecimal("399.00"), new BigDecimal("4.8"), 89,
                "Kanke Road, Ranchi, Jharkhand 834006, India", "Kanke", 23.4150, 85.3220, 11.0,
                "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=400&q=80",
                List.of("Gardening and Home Maintenance"),
                "Lawn Mowing", "Garden Maintenance", "Plant Care"),

            new ProviderDefinition("demo_prov_gard_3@localserviceconnect.com", "Arjun Munda",
                "Ranchi Handyman & Home Maintenance",
                "All-round household maintenance specialist: curtain rod installation, wall mirror drill mounting, door hardware tightening, odd-job repairs & handyman service.",
                6, new BigDecimal("349.00"), new BigDecimal("4.7"), 74,
                "Bariatu, Ranchi, Jharkhand 834009, India", "Bariatu", 23.3850, 85.3600, 10.0,
                "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=400&q=80",
                List.of("Gardening and Home Maintenance"),
                "General Home Maintenance", "Plant Care", "Garden Maintenance")
        );

        List<ServiceType> allServiceTypes = serviceTypeRepository.findAll();
        int addedCount = 0;
        for (ProviderDefinition pd : defs) {
            if (userRepository.existsByEmail(pd.email)) {
                // Idempotently ensure existing demo provider has canonical services linked and updated sub-services
                providerRepository.findByUserEmail(pd.email).ifPresent(p -> {
                    boolean changed = false;
                    if (p.getBusinessName() != null && (!p.getBusinessName().equals(pd.businessName) || p.getBusinessName().contains("[DEMO]"))) {
                        p.setBusinessName(pd.businessName);
                        changed = true;
                    }
                    if (p.isDemo() != null && p.isDemo()) {
                        p.setIsDemo(false);
                        changed = true;
                    }
                    for (String sName : pd.serviceNames) {
                        for (ServiceType st : allServiceTypes) {
                            if (st.getName().equalsIgnoreCase(sName) && !p.getServices().contains(st)) {
                                p.getServices().add(st);
                                changed = true;
                            }
                        }
                    }
                    if (changed) {
                        providerRepository.save(p);
                    }

                    // Update subservices if not matching
                    List<ProviderSubService> currentSubs = providerSubServiceRepository.findByProviderIdAndEnabledTrue(p.getId());
                    boolean hasSub1 = currentSubs.stream().anyMatch(s -> s.getName().equalsIgnoreCase(pd.sub1));
                    if (!hasSub1) {
                        createProviderSubService(p.getId(), pd.sub1, pd.serviceNames.get(0), "Doorstep execution using professional specialized equipment.", pd.price, "30 mins");
                        createProviderSubService(p.getId(), pd.sub2, pd.serviceNames.get(0), "Comprehensive diagnosis and precision servicing.", pd.price.add(new BigDecimal("100.00")), "45 mins");
                        createProviderSubService(p.getId(), pd.sub3, pd.serviceNames.get(0), "Advanced overhaul and deep maintenance package.", pd.price.add(new BigDecimal("250.00")), "60 mins");
                    }
                });
                continue;
            }

            // 1. Create User
            User u = new User();
            u.setName(pd.fullName);
            u.setEmail(pd.email);
            u.setPhone("98" + (int)(10000000 + Math.random() * 89999999));
            u.setPassword(passwordEncoder.encode("Provider@12345"));
            u.setRole(Role.PROVIDER);
            u.setEnabled(true);
            u = userRepository.save(u);

            // 2. Create Provider
            Provider p = new Provider();
            p.setUser(u);
            p.setBusinessName(pd.businessName);
            p.setDescription(pd.description);
            p.setExperienceYears(pd.experienceYears);
            p.setApproxPrice(pd.price);
            p.setVerificationStatus(VerificationStatus.VERIFIED);
            p.setAvailability(true);
            p.setRating(pd.rating);
            p.setReviewCount(pd.reviewCount);
            p.setFormattedAddress(pd.address);
            p.setLatitude(pd.lat);
            p.setLongitude(pd.lon);
            p.setServiceRadiusKm(pd.radiusKm);
            p.setProfileImage(pd.profileImage);
            p.setWorkingHours("Mon - Sun: 8:00 AM - 8:00 PM");
            p.setIsDemo(false);

            // Link services
            for (String sName : pd.serviceNames) {
                for (ServiceType st : allServiceTypes) {
                    if (st.getName().equalsIgnoreCase(sName) && !p.getServices().contains(st)) {
                        p.getServices().add(st);
                    }
                }
            }
            Provider saved = providerRepository.save(p);

            // 3. Provider Areas
            ProviderArea pa1 = new ProviderArea();
            pa1.setProviderId(saved.getId());
            pa1.setArea(pd.area);
            pa1.setCity("Ranchi");
            providerAreaRepository.save(pa1);

            ProviderArea pa2 = new ProviderArea();
            pa2.setProviderId(saved.getId());
            pa2.setArea("Ranchi");
            pa2.setCity("Ranchi");
            providerAreaRepository.save(pa2);

            // 4. Seed Provider Sub-Services matching canonical subservices
            String primaryCat = pd.serviceNames.isEmpty() ? "Service" : pd.serviceNames.get(0);
            createProviderSubService(saved.getId(), pd.sub1, primaryCat, "Doorstep execution using professional specialized equipment.", pd.price, "30 mins");
            createProviderSubService(saved.getId(), pd.sub2, primaryCat, "Comprehensive diagnosis and precision servicing.", pd.price.add(new BigDecimal("100.00")), "45 mins");
            createProviderSubService(saved.getId(), pd.sub3, primaryCat, "Advanced overhaul and deep maintenance package.", pd.price.add(new BigDecimal("250.00")), "60 mins");

            // 5. Seed Portfolio (Before & After)
            ProviderPortfolio pf = new ProviderPortfolio();
            pf.setProviderId(saved.getId());
            pf.setTitle(pd.sub1 + " - Before & After");
            pf.setSubService(pd.sub1);
            pf.setBeforeImageUrl("https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=400&q=80");
            pf.setAfterImageUrl("https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=400&q=80");
            pf.setDescription("Complete transformation performed with certified parts and guaranteed satisfaction.");
            pf.setCompletedDate("Recent Project, Ranchi");
            portfolioRepository.save(pf);

            // 6. Seed Work Video
            ProviderVideo pv = new ProviderVideo();
            pv.setProviderId(saved.getId());
            pv.setTitle(pd.sub1 + " Process Walkthrough");
            pv.setSubService(pd.sub1);
            pv.setVideoUrl("https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4");
            pv.setThumbnailUrl(pd.profileImage);
            pv.setDuration("45 sec");
            pv.setDescription("Step-by-step verified doorstep execution adhering to safety protocols.");
            videoRepository.save(pv);

            addedCount++;
        }

        if (addedCount > 0) {
            log.info("Successfully seeded {} realistic Indian demo providers across 12 canonical service categories.", addedCount);
        } else {
            log.info("Demo providers verified. No duplicate records created.");
        }
    }

    private void createProviderSubService(Long providerId, String name, String category, String description, BigDecimal price, String duration) {
        ProviderSubService sub = new ProviderSubService();
        sub.setProviderId(providerId);
        sub.setName(name);
        sub.setCategory(category);
        sub.setDescription(description);
        sub.setPrice(price);
        sub.setDuration(duration);
        sub.setImageUrl("https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=400&q=80");
        sub.setEnabled(true);
        providerSubServiceRepository.save(sub);
    }

    private record CategorySeed(String name, String description, String icon) {}

    private record SubServiceSeed(String name, String description, BigDecimal price, String duration, String imageUrl, int displayOrder) {}

    private record ProviderDefinition(
        String email,
        String fullName,
        String businessName,
        String description,
        int experienceYears,
        BigDecimal price,
        BigDecimal rating,
        int reviewCount,
        String address,
        String area,
        double lat,
        double lon,
        double radiusKm,
        String profileImage,
        List<String> serviceNames,
        String sub1,
        String sub2,
        String sub3
    ) {}
}
