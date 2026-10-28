/**
 * Local Service Connect - Comprehensive Indian Home Services Catalog
 * 12 Canonical Categories & 86 Professional Sub-Services (Urban Company Inspired)
 * Verified Indian pricing (INR), durations, step-by-step processes, and inclusions.
 */

const SUB_SERVICES_DATA = {
  // 1. Cleaning Services
  "Cleaning Services": {
    categoryTitle: "Cleaning Services",
    icon: "bi-stars",
    rating: 4.88,
    totalBookings: "4.8 M bookings",
    description: "Deep home scrubbing, sofa shampooing, bathroom sanitization, kitchen degreasing & move-in cleaning",
    subServices: [
      {
        id: "clean-sofa",
        name: "Sofa Cleaning",
        shortName: "Sofa Cleaning",
        rating: 4.89,
        reviewsCount: "380K reviews",
        price: 699,
        duration: "60 mins",
        icon: "bi-couch",
        thumbnail: "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Dry vacuuming of sofa fabric/leather to remove dust and allergens",
          "Chemical foam shampooing and rotary scrubber stain spot treatment",
          "High-power moisture extraction with 95% drying efficiency"
        ],
        exclusions: ["Permanent oil paint or ink stain removal"],
        steps: ["Dry vacuuming", "Shampoo application", "Rotary scrub", "Moisture extraction"]
      },
      {
        id: "clean-kitchen",
        name: "Kitchen Deep Cleaning",
        shortName: "Kitchen Deep Clean",
        rating: 4.87,
        reviewsCount: "290K reviews",
        price: 799,
        duration: "90 mins",
        icon: "bi-cup-hot",
        thumbnail: "https://images.unsplash.com/photo-1556911220-e15b29be8c8f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Chimney baffle filter dip wash and exhaust fan degreasing",
          "Gas stove burner carbon cleaning and slab oil stain removal",
          "Modular cabinet interior wiping and tile splashback scrubbing"
        ],
        exclusions: ["Inside utensil washing or appliance repair"],
        steps: ["Degreaser spray", "Chimney filter soak", "Countertop scrub", "Cabinet wipe-down"]
      },
      {
        id: "clean-bath",
        name: "Bathroom Cleaning",
        shortName: "Bathroom Cleaning",
        rating: 4.90,
        reviewsCount: "410K reviews",
        price: 499,
        duration: "45 mins",
        icon: "bi-water",
        thumbnail: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Rotary tile scrubbing for yellow limescale and hard water stains",
          "Chrome tap and shower head descaling with safe citric acid",
          "Toilet commode, cistern and washbasin germicidal sanitization"
        ],
        exclusions: ["Plumbing pipe leakage or tile re-grouting"],
        steps: ["Hard water descaling", "Rotary floor scrub", "Fixture buffing", "Deodorizing rinse"]
      },
      {
        id: "clean-mattress",
        name: "Mattress Cleaning",
        shortName: "Mattress Cleaning",
        rating: 4.86,
        reviewsCount: "140K reviews",
        price: 599,
        duration: "45 mins",
        icon: "bi-heart-pulse",
        thumbnail: "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Industrial vacuum extraction of bed dust mites and skin flakes",
          "Eco-friendly anti-allergen enzyme foam shampooing",
          "Moisture extraction and citrus deodorization"
        ],
        exclusions: ["Torn fabric stitching"],
        steps: ["Dry dust extraction", "Anti-allergen foam", "Steam sanitize", "Moisture suction"]
      },
      {
        id: "clean-carpet",
        name: "Carpet Cleaning",
        shortName: "Carpet Cleaning",
        rating: 4.85,
        reviewsCount: "170K reviews",
        price: 499,
        duration: "45 mins",
        icon: "bi-border-style",
        thumbnail: "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "High suction deep pile vacuuming for dirt and sand particles",
          "Fabric-safe detergent shampooing with soft bristle brush",
          "Extraction and pile fluffing for revitalized carpet texture"
        ],
        exclusions: ["Repairing burnt fiber marks"],
        steps: ["Vacuum extraction", "Shampoo application", "Rotary pile brush", "Dry extraction"]
      },
      {
        id: "clean-fullhome",
        name: "Full Home Deep Cleaning",
        shortName: "Full Home Cleaning",
        rating: 4.92,
        reviewsCount: "520K reviews",
        price: 1499,
        duration: "180 mins",
        icon: "bi-house-heart",
        thumbnail: "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Complete room scrubbing: bedrooms, living, kitchen and bathrooms",
          "Single-disc mechanical floor buffing and high-gloss scrub",
          "Balcony, glass windows, door frames, switchboards and fan cleaning"
        ],
        exclusions: ["Wall painting or chandelier crystal dismantling"],
        steps: ["Dry dusting & cobweb clearing", "Floor machine scrub", "Kitchen/Bath sanitize", "Final inspection"]
      },
      {
        id: "clean-bedroom",
        name: "Bedroom Cleaning",
        shortName: "Bedroom Cleaning",
        rating: 4.84,
        reviewsCount: "110K reviews",
        price: 399,
        duration: "45 mins",
        icon: "bi-lamp",
        thumbnail: "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Ceiling fan, window glass, wardrobe exterior and door wipe",
          "Floor sweeping, disinfectant mopping and stain removal",
          "Under-bed vacuuming and bed linen tidying"
        ],
        exclusions: ["Wardrobe interior reorganizing"],
        steps: ["Ceiling dust removal", "Furniture wipedown", "Floor sanitization", "Linen arranging"]
      },
      {
        id: "clean-balcony",
        name: "Balcony Cleaning",
        shortName: "Balcony Cleaning",
        rating: 4.83,
        reviewsCount: "95K reviews",
        price: 349,
        duration: "30 mins",
        icon: "bi-sun",
        thumbnail: "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Pressure water floor wash and bird dropping removal",
          "Railing dust, rust spots and cobweb cleaning",
          "Drain grating cleanout to prevent rainwater pooling"
        ],
        exclusions: ["Exterior facade high-rope cleaning"],
        steps: ["Dry sweep & trash clearing", "Railing scrub", "Floor pressure wash", "Drain clearing"]
      },
      {
        id: "clean-movein",
        name: "Move-in/Move-out Cleaning",
        shortName: "Move-in/Move-out",
        rating: 4.89,
        reviewsCount: "210K reviews",
        price: 1299,
        duration: "150 mins",
        icon: "bi-box-arrow-in-right",
        thumbnail: "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Intensive deep scrub for vacant apartments before possession or handover",
          "Inside and outside cabinet cleaning, paint splash removal on floors",
          "Full bathroom and kitchen acid-free sterilization"
        ],
        exclusions: ["Moving furniture boxes"],
        steps: ["Debris collection", "Cabinet interior wash", "Bathroom deep scrub", "Machine floor polish"]
      },
      {
        id: "clean-office",
        name: "Office Cleaning",
        shortName: "Office Cleaning",
        rating: 4.88,
        reviewsCount: "135K reviews",
        price: 999,
        duration: "120 mins",
        icon: "bi-building",
        thumbnail: "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Workstation table sanitization, monitor dust wiping and phone disinfect",
          "Conference room carpet vacuuming and glass partition buffing",
          "Pantry slab degreasing and office washroom hygiene sterilization"
        ],
        exclusions: ["Server room electrical hardware maintenance"],
        steps: ["Trash disposal", "Desk sanitized wipe", "Carpet vacuuming", "Restroom sanitize"]
      }
    ],
    reviews: [
      { author: "Sneha Sinha", rating: 5, date: "Yesterday", comment: "The sofa and carpet deep clean was unbelievable. 3-year old tea stains completely vanished!" },
      { author: "Vivek Agarwal", rating: 5, date: "3 days ago", comment: "Kitchen deep cleaning team degreased my chimney and stove in Morabadi. Highly professional." }
    ]
  },

  // 2. Electrician Services
  "Electrician Services": {
    categoryTitle: "Electrician Services",
    icon: "bi-lightning-charge",
    rating: 4.89,
    totalBookings: "5.1 M bookings",
    description: "Certified electricians for fan repair, switchboards, MCB, inverter, smart doorbells and electrical fault diagnosis",
    subServices: [
      {
        id: "elec-fan",
        name: "Fan Installation and Repair",
        shortName: "Fan Service",
        rating: 4.89,
        reviewsCount: "430K reviews",
        price: 199,
        duration: "30 mins",
        icon: "bi-fan",
        thumbnail: "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Ceiling/exhaust fan mounting and hook safety anchor check",
          "Capacitor testing, blade alignment balancing and noise reduction",
          "Speed regulator connection and earthing continuity test"
        ],
        exclusions: ["Cost of burnt motor rewinding"],
        steps: ["Power isolation", "Capacitor health check", "Mounting & wiring", "Speed regulator test"]
      },
      {
        id: "elec-switch",
        name: "Switch and Socket Repair",
        shortName: "Switch & Socket",
        rating: 4.87,
        reviewsCount: "320K reviews",
        price: 149,
        duration: "25 mins",
        icon: "bi-toggles",
        thumbnail: "https://images.unsplash.com/photo-1558494949-ef010cbdcc31?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Burnt switch, 16A power socket and faceplate replacement",
          "Gang box terminal tightening to eliminate sparks",
          "Multimeter voltage continuity and earthing test"
        ],
        exclusions: ["Cost of designer modular plates"],
        steps: ["Mains MCB turn-off", "Old module removal", "Terminal termination", "Load testing"]
      },
      {
        id: "elec-light",
        name: "Light Installation",
        shortName: "Light Install",
        rating: 4.88,
        reviewsCount: "160K reviews",
        price: 249,
        duration: "35 mins",
        icon: "bi-lamp",
        thumbnail: "https://images.unsplash.com/photo-1517991104123-1d56a6e81ed9?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Ceiling LED downlights, decorative batten and pendant mounting",
          "Chandelier anchor drill and balanced crystal hanging",
          "Cove warm white LED strip driver connection"
        ],
        exclusions: ["Cost of chandelier fixture"],
        steps: ["Ceiling anchor drill", "Phase/neutral wiring", "Fixture mounting", "Switching verification"]
      },
      {
        id: "elec-wiring",
        name: "Wiring and Rewiring",
        shortName: "Wiring & Rewiring",
        rating: 4.86,
        reviewsCount: "195K reviews",
        price: 499,
        duration: "60 mins",
        icon: "bi-diagram-3",
        thumbnail: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Concealed PVC conduit fish tape wire pulling",
          "Short circuit tracing and neutral-ground continuity restoration",
          "Heavy gauge wire installation for AC and geyser points"
        ],
        exclusions: ["Wall masonry plastering"],
        steps: ["Megger insulation test", "Fault branch isolation", "Wire replacement", "Circuit load test"]
      },
      {
        id: "elec-mcb",
        name: "MCB and Fuse Repair",
        shortName: "MCB & Fuse",
        rating: 4.90,
        reviewsCount: "180K reviews",
        price: 299,
        duration: "30 mins",
        icon: "bi-lightning-charge-fill",
        thumbnail: "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Tripping MCB diagnosis and branch short-circuit line isolation",
          "Single-pole/double-pole MCB replacement and phase balancing",
          "Earthing safety test and RCCB sensitivity check"
        ],
        exclusions: ["High-voltage transformer lines"],
        steps: ["Breaker trip inspection", "Circuit fault isolation", "DIN rail breaker mount", "Phase balancing"]
      },
      {
        id: "elec-inverter",
        name: "Inverter Installation and Repair",
        shortName: "Inverter & UPS",
        rating: 4.88,
        reviewsCount: "140K reviews",
        price: 399,
        duration: "45 mins",
        icon: "bi-battery-charging",
        thumbnail: "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Inverter UPS unit mounting and mains selector bypass switch wiring",
          "Tubular battery terminal petroleum jelly application & acid gravity check",
          "Full load test during simulated grid power failure"
        ],
        exclusions: ["Battery replacement cost"],
        steps: ["Battery terminal prep", "Inverter wiring & bypass", "Distilled water top-up", "Mains cutoff test"]
      },
      {
        id: "elec-doorbell",
        name: "Doorbell Installation",
        shortName: "Doorbell Service",
        rating: 4.85,
        reviewsCount: "95K reviews",
        price: 199,
        duration: "25 mins",
        icon: "bi-bell",
        thumbnail: "https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Wired electronic chime mounting and pushbutton connection",
          "Smart Wi-Fi video doorbell wall mounting and mobile app pairing",
          "Transformer voltage test and neat wire casing"
        ],
        exclusions: ["Cost of video doorbell hardware"],
        steps: ["Wall drill & bracket", "Chime line wiring", "Wi-Fi app pairing", "Audio/chime test"]
      },
      {
        id: "elec-fault",
        name: "Electrical Fault Diagnosis",
        shortName: "Fault Diagnosis",
        rating: 4.91,
        reviewsCount: "220K reviews",
        price: 299,
        duration: "40 mins",
        icon: "bi-search",
        thumbnail: "https://images.unsplash.com/photo-1621905251918-48416bd8575a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "End-to-end electrical inspection of switches, wiring, and DB box",
          "Earthing leakage test and neutral voltage drop measurement",
          "Detailed estimate and immediate hazard resolution"
        ],
        exclusions: ["Cost of heavy replacement cables"],
        steps: ["Safety walkthrough", "Multimeter probe testing", "Leakage isolation", "Written audit report"]
      }
    ],
    reviews: [
      { author: "Rajesh Murmu", rating: 5, date: "2 days ago", comment: "The electrician resolved an MCB tripping issue in Doranda within 30 minutes. Super knowledgeable." },
      { author: "Ananya Roy", rating: 5, date: "1 week ago", comment: "Installed 3 ceiling fans and replaced switchboards neatly. Very polite." }
    ]
  },

  // 3. Plumbing Services
  "Plumbing Services": {
    categoryTitle: "Plumbing Services",
    icon: "bi-droplet",
    rating: 4.87,
    totalBookings: "4.3 M bookings",
    description: "Expert plumbers for tap repair, pipe leakages, washbasin, toilet cisterns, drain clearing and overhead tank cleaning",
    subServices: [
      {
        id: "plumb-tap",
        name: "Tap Repair",
        shortName: "Tap Repair",
        rating: 4.88,
        reviewsCount: "350K reviews",
        price: 149,
        duration: "25 mins",
        icon: "bi-droplet-half",
        thumbnail: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Dripping tap spindle replacement and ceramic disc washer fitting",
          "Aerator cleaning to clear rust debris and restore full water flow",
          "Teflon tape seal around angle cock and water pressure check"
        ],
        exclusions: ["Cost of high-end brass tap body"],
        steps: ["Water supply cutoff", "Tap bonnet disassembly", "Spindle replacement", "Pressure leak test"]
      },
      {
        id: "plumb-washbasin",
        name: "Washbasin Installation",
        shortName: "Washbasin",
        rating: 4.86,
        reviewsCount: "180K reviews",
        price: 349,
        duration: "45 mins",
        icon: "bi-water",
        thumbnail: "https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Wall-hung or countertop washbasin bracket mounting and leveling",
          "Waste coupling fitting, bottle trap connection & anti-cockroach silicone seal",
          "Flexible hot/cold braided hose pipe connection"
        ],
        exclusions: ["Basin ceramic vanity cost"],
        steps: ["Wall bracket drilling", "Basin anchor fixing", "Bottle trap install", "Silicone waterproofing"]
      },
      {
        id: "plumb-toilet",
        name: "Toilet Repair",
        shortName: "Toilet Repair",
        rating: 4.87,
        reviewsCount: "260K reviews",
        price: 249,
        duration: "35 mins",
        icon: "bi-badge-wc",
        thumbnail: "https://images.unsplash.com/photo-1585058177595-38b8162e08e6?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Continuous toilet flush tank siphon valve repair and float adjustment",
          "Jet spray, health faucet and angle cock replacement",
          "Commode seat cover hinge fitting and silicone perimeter seal"
        ],
        exclusions: ["Ceramic bowl replacement"],
        steps: ["Cistern water drainage", "Flush valve replacement", "Health faucet test", "Perimeter silicone seal"]
      },
      {
        id: "plumb-leak",
        name: "Water Leakage Repair",
        shortName: "Leakage Repair",
        rating: 4.89,
        reviewsCount: "290K reviews",
        price: 399,
        duration: "50 mins",
        icon: "bi-moisture",
        thumbnail: "https://images.unsplash.com/photo-1607472586893-edb57bdc0e39?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Concealed wall acoustic leak detection and pinhole diagnosis",
          "CPVC/GI pipe joint cutting, solvent cement weld and coupling fit",
          "Hydrostatic pressure testing to guarantee leak-free seal"
        ],
        exclusions: ["Tile masonry reconstruction"],
        steps: ["Acoustic leak trace", "Wall opening & pipe cut", "Solvent coupling bond", "Hydrostatic pressure test"]
      },
      {
        id: "plumb-pipe",
        name: "Pipe Installation and Repair",
        shortName: "Pipe Install",
        rating: 4.85,
        reviewsCount: "175K reviews",
        price: 449,
        duration: "60 mins",
        icon: "bi-bezier2",
        thumbnail: "https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Fresh CPVC/UPVC water pipeline routing with pipe clips",
          "Gate valve, brass non-return valve and union fitting",
          "Expansion joint sealing and water pump inlet connection"
        ],
        exclusions: ["Cost of pipes and fittings"],
        steps: ["Route measurement", "Pipe cutting & deburring", "Solvent bonding", "Full pressure check"]
      },
      {
        id: "plumb-drain",
        name: "Drain Cleaning",
        shortName: "Drain Cleaning",
        rating: 4.88,
        reviewsCount: "310K reviews",
        price: 299,
        duration: "40 mins",
        icon: "bi-funnel",
        thumbnail: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Motorized mechanical steel snake de-clogging for kitchen sinks & traps",
          "Hair, soap scum, and grease trap clearance with high flow flush",
          "Enzyme odor neutralizer treatment"
        ],
        exclusions: ["Main municipal deep street sewer clearing"],
        steps: ["Trap inspection", "Motorized snaking", "Hot chemical flush", "High flow drainage check"]
      },
      {
        id: "plumb-shower",
        name: "Shower Installation",
        shortName: "Shower Install",
        rating: 4.86,
        reviewsCount: "130K reviews",
        price: 249,
        duration: "30 mins",
        icon: "bi-cloud-drizzle",
        thumbnail: "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Overhead rain shower arm and shower head mounting",
          "Hand shower wall bracket anchor fixing and stainless hose connection",
          "Hot/cold water mixer cartridge calibration"
        ],
        exclusions: ["Cost of luxury shower panel"],
        steps: ["Wall outlet fitting", "Shower arm mounting", "Teflon thread seal", "Spray pattern test"]
      },
      {
        id: "plumb-tank",
        name: "Water Tank Cleaning",
        shortName: "Water Tank Clean",
        rating: 4.90,
        reviewsCount: "210K reviews",
        price: 599,
        duration: "75 mins",
        icon: "bi-inbox-fill",
        thumbnail: "https://images.unsplash.com/photo-1507652313519-d4e9174996dd?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Overhead Sintex/concrete water tank draining and motorized de-sludging",
          "High-pressure jet wash scrub of inner walls, corners and ceiling",
          "UV lamp germicidal disinfection and potassium permanganate treatment"
        ],
        exclusions: ["Water pump mechanical repair"],
        steps: ["Sludge pumpout", "Rotary jet wall scrub", "UV germicidal scan", "Clean water refill"]
      }
    ],
    reviews: [
      { author: "Deepak Kumar", rating: 5, date: "3 days ago", comment: "Water tank cleaning was spotless. Drained all sludge and sanitized our 1000L tank in Lalpur." },
      { author: "Sunita Toppo", rating: 5, date: "1 week ago", comment: "Fixed continuous dripping tap and toilet flush in one visit. Highly recommend!" }
    ]
  },

  // 4. AC and Appliance Repair
  "AC and Appliance Repair": {
    categoryTitle: "AC and Appliance Repair",
    icon: "bi-snow",
    rating: 4.89,
    totalBookings: "5.4 M bookings",
    description: "Certified technicians for AC servicing, refrigerator cooling, washing machines, microwaves, RO purifiers and water geysers",
    subServices: [
      {
        id: "ac-install",
        name: "AC Installation",
        shortName: "AC Installation",
        rating: 4.88,
        reviewsCount: "220K reviews",
        price: 799,
        duration: "90 mins",
        icon: "bi-snow2",
        thumbnail: "https://images.unsplash.com/photo-1621905252507-b35492cc74b4?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Indoor unit metal plate mounting with spirit level calibration",
          "Heavy duty outdoor cantilever bracket installation and vibration pads",
          "Copper refrigerant line flaring, vacuum pump dry test & gas charge check"
        ],
        exclusions: ["Extra copper pipe length exceeding 3 meters"],
        steps: ["Core wall drill", "Indoor plate mounting", "Outdoor bracket fix", "Vacuum leak test"]
      },
      {
        id: "ac-repair",
        name: "AC Repair",
        shortName: "AC Repair",
        rating: 4.89,
        reviewsCount: "340K reviews",
        price: 399,
        duration: "45 mins",
        icon: "bi-wrench-adjustable",
        thumbnail: "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Cooling diagnosis, thermostat temperature sensor recalibration",
          "Compressor starting capacitor testing & inverter PCB motherboard repair",
          "Water leakage tray unblocking and blower noise troubleshooting"
        ],
        exclusions: ["Compressor replacement cost"],
        steps: ["Multimeter & amp check", "Capacitor test", "PCB board repair", "Air throw temp test"]
      },
      {
        id: "ac-service",
        name: "AC Servicing",
        shortName: "AC Servicing",
        rating: 4.91,
        reviewsCount: "510K reviews",
        price: 499,
        duration: "60 mins",
        icon: "bi-wind",
        thumbnail: "https://images.unsplash.com/photo-1581092335397-9583fe92d232?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "High-pressure foam jet coil deep cleaning with zero water splash jacket",
          "Outdoor condenser coil chemical descaling to remove dirt and heat trap",
          "Filter washing, drain tray flush and refrigerant gas pressure check"
        ],
        exclusions: ["Refrigerant gas refill if empty"],
        steps: ["Jacket protection setup", "Foam jet wash", "Condenser coil spray", "Cooling temp measurement"]
      },
      {
        id: "app-fridge",
        name: "Refrigerator Repair",
        shortName: "Fridge Repair",
        rating: 4.88,
        reviewsCount: "280K reviews",
        price: 399,
        duration: "50 mins",
        icon: "bi-thermometer-snow",
        thumbnail: "https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Single/double door compressor relay and PTC starter check",
          "Refrigerant gas leakage testing, filter drier change and vacuum charging",
          "Defrost timer, bimetal sensor and rubber door gasket alignment"
        ],
        exclusions: ["New compressor hardware cost"],
        steps: ["Capillary inspection", "Relay continuity test", "Gas recharge", "Freezer pull-down test"]
      },
      {
        id: "app-wash",
        name: "Washing Machine Repair",
        shortName: "Washing Machine",
        rating: 4.87,
        reviewsCount: "310K reviews",
        price: 349,
        duration: "45 mins",
        icon: "bi-arrow-repeat",
        thumbnail: "https://images.unsplash.com/photo-1626806787461-102c1bfaaea1?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Front/Top load drum noise, bearing and shock absorber repair",
          "Drain pump de-clogging and error code E2/OE resolution",
          "Inlet solenoid valve replacement and electronic PCB control repair"
        ],
        exclusions: ["Full drum replacement"],
        steps: ["Digital error diagnostic", "Drain pump de-clog", "Spin belt adjustment", "Rinse cycle test"]
      },
      {
        id: "app-micro",
        name: "Microwave Repair",
        shortName: "Microwave Repair",
        rating: 4.86,
        reviewsCount: "160K reviews",
        price: 299,
        duration: "40 mins",
        icon: "bi-hdd-network",
        thumbnail: "https://images.unsplash.com/photo-1585659722983-3a675dabf23d?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Magnetron heating element test and microwave diode replacement",
          "Touch panel keypad repair, door interlock switch replacement",
          "Glass turntable roller ring and motor alignment"
        ],
        exclusions: ["Cost of new magnetron"],
        steps: ["High voltage capacitor discharge", "Magnetron test", "Door safety switch check", "Water heat test"]
      },
      {
        id: "app-ro",
        name: "RO Service",
        shortName: "RO Service",
        rating: 4.90,
        reviewsCount: "370K reviews",
        price: 349,
        duration: "40 mins",
        icon: "bi-droplet-half",
        thumbnail: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Sediment filter, activated carbon block and pre-filter candle flush",
          "80 GPD RO membrane tuning, auto-cutoff float check and TDS calibration",
          "Booster pump pressure check and push-fit elbow leakage resolution"
        ],
        exclusions: ["New RO membrane cost"],
        steps: ["TDS measurement", "Filter bowl cleaning", "Booster pump tune", "Final TDS verification"]
      },
      {
        id: "app-geyser",
        name: "Geyser Repair",
        shortName: "Geyser Repair",
        rating: 4.88,
        reviewsCount: "250K reviews",
        price: 299,
        duration: "35 mins",
        icon: "bi-fire",
        thumbnail: "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Instant/storage geyser copper heating coil element replacement",
          "Dual safety thermostat tripping reset and thermal cutout check",
          "Pressure relief safety valve check and braided pipe replacement"
        ],
        exclusions: ["Inner tank welding"],
        steps: ["Electrical safety isolate", "Element descaling", "Thermostat reset", "Water heating test"]
      }
    ],
    reviews: [
      { author: "Pooja Chawla", rating: 5, date: "Yesterday", comment: "AC jet service dropped my room cooling time in half. Technician Sunil was very thorough." },
      { author: "Kishore Paswan", rating: 5, date: "4 days ago", comment: "Washing machine drain pump error fixed in 20 minutes with genuine part in Harmu." }
    ]
  },

  // 5. Salon and Grooming
  "Salon and Grooming": {
    categoryTitle: "Salon and Grooming",
    icon: "bi-scissors",
    rating: 4.91,
    totalBookings: "3.9 M bookings",
    description: "Hygienic salon at home: haircuts, beard styling, waxing, facial treatments, hair spa and bridal makeover",
    subServices: [
      {
        id: "salon-men-hair",
        name: "Men's Haircut",
        shortName: "Men's Haircut",
        rating: 4.90,
        reviewsCount: "420K reviews",
        price: 199,
        duration: "30 mins",
        icon: "bi-scissors",
        thumbnail: "https://images.unsplash.com/photo-1503951914875-452162b0f3f1?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Precision haircut, taper fade or classic scissor cut by certified barber",
          "Single-use sterile sheet, sanitized clipper guards and comb set",
          "Post-cut neck cleanup, hair wash and refreshing blow-dry styling"
        ],
        exclusions: ["Hair coloring"],
        steps: ["Hair consultation", "Clipper & scissor styling", "Neck razor trim", "Vacuum & tidy cleanup"]
      },
      {
        id: "salon-beard",
        name: "Beard Trimming",
        shortName: "Beard Trim",
        rating: 4.89,
        reviewsCount: "310K reviews",
        price: 149,
        duration: "20 mins",
        icon: "bi-person",
        thumbnail: "https://images.unsplash.com/photo-1621605815971-fbc98d665033?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Beard sculpting, fade blending and razor-sharp cheekline outline",
          "Hot towel facial steam to open pores and soften stubble",
          "Soothing organic beard oil massage and aftershave lotion"
        ],
        exclusions: ["Beard color dyeing"],
        steps: ["Beard comb & trim", "Hot towel steam", "Razor cheek outline", "Beard oil massage"]
      },
      {
        id: "salon-hairstyling",
        name: "Hair Styling",
        shortName: "Hair Styling",
        rating: 4.88,
        reviewsCount: "190K reviews",
        price: 349,
        duration: "40 mins",
        icon: "bi-magic",
        thumbnail: "https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Professional blow-dry, curling iron waves or sleek straightening",
          "Heat protection serum application and hair volume boosting",
          "Long-lasting setting spray for weddings and evening parties"
        ],
        exclusions: ["Hair wash sink facility"],
        steps: ["Heat protectant spray", "Sectioning & iron styling", "Texture definition", "Setting mist"]
      },
      {
        id: "salon-facial",
        name: "Facial",
        shortName: "Facial Treatment",
        rating: 4.93,
        reviewsCount: "380K reviews",
        price: 599,
        duration: "60 mins",
        icon: "bi-emoji-smile",
        thumbnail: "https://images.unsplash.com/photo-1570172619644-dfd03ed5d881?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "O3+ Gold Radiance / Charcoal Detox deep facial at home",
          "Gentle walnut scrub exfoliation, blackhead extraction and face steam",
          "Aromatherapy acupressure face massage and peel-off glow mask"
        ],
        exclusions: ["Chemical peel surgery"],
        steps: ["Deep pore cleanse", "Face steam & scrub", "Acupressure massage", "Glow pack & tone"]
      },
      {
        id: "salon-colour",
        name: "Hair Colouring",
        shortName: "Hair Colouring",
        rating: 4.87,
        reviewsCount: "170K reviews",
        price: 499,
        duration: "60 mins",
        icon: "bi-palette",
        thumbnail: "https://images.unsplash.com/photo-1562322140-8baeececf3df?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Root touch-up or global grey hair coverage with ammonia-free products",
          "Even brush application with skin barrier cream to prevent stains",
          "Color-lock conditioning hair rinse and blow-dry styling"
        ],
        exclusions: ["Cost of high-end international dye box if user supplies"],
        steps: ["Hair sectioning", "Barrier cream application", "Even color brush", "Conditioning rinse"]
      },
      {
        id: "salon-headmassage",
        name: "Head Massage",
        shortName: "Head Massage",
        rating: 4.92,
        reviewsCount: "280K reviews",
        price: 249,
        duration: "30 mins",
        icon: "bi-stars",
        thumbnail: "https://images.unsplash.com/photo-1519823551278-64ac92734fb1?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Warm Ayurvedic Bhringraj / almond oil scalp nourishment",
          "Deep tissue pressure point massage releasing neck and shoulder stress",
          "Hot herbal towel wrap for maximum root absorption"
        ],
        exclusions: ["Medicated baldness treatment"],
        steps: ["Warm oil warm-up", "Acupressure scalp massage", "Neck & shoulder release", "Hot towel steam"]
      },
      {
        id: "salon-women-hair",
        name: "Women's Haircut",
        shortName: "Women's Haircut",
        rating: 4.91,
        reviewsCount: "340K reviews",
        price: 399,
        duration: "45 mins",
        icon: "bi-scissors",
        thumbnail: "https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Trendy layer cut, feather cut, blunt bob or split-ends trim",
          "Face structure consultation by senior female stylist",
          "Hair serum application and bouncy outward blow-dry styling"
        ],
        exclusions: ["Keratin smoothening treatment"],
        steps: ["Hair analysis", "Sectioning & precision cut", "Length check", "Volume blow-dry styling"]
      },
      {
        id: "salon-bridal",
        name: "Bridal Makeup",
        shortName: "Bridal Makeup",
        rating: 4.95,
        reviewsCount: "120K reviews",
        price: 1999,
        duration: "120 mins",
        icon: "bi-gem",
        thumbnail: "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "HD party or wedding bridal makeup with sweat-proof airbrush finish",
          "Premium false eyelashes, contact lenses fitting and lipstick setting",
          "Designer saree/lehenga draping with pin placement and floral bun styling"
        ],
        exclusions: ["Bridal jewelry rental"],
        steps: ["Skin prep & prime", "HD foundation & contour", "Eye makeup & lashes", "Draping & floral hair updo"]
      }
    ],
    reviews: [
      { author: "Kavita Sen", rating: 5, date: "3 days ago", comment: "Priya did my party makeup and layer cut at home. Zero salon wait and 5-star hygiene!" },
      { author: "Amit Pandey", rating: 5, date: "1 week ago", comment: "The fade haircut and hot towel beard trim was as good as any luxury salon in Ranchi." }
    ]
  },

  // 6. Vehicle Services
  "Vehicle Services": {
    categoryTitle: "Vehicle Services",
    icon: "bi-car-front",
    rating: 4.88,
    totalBookings: "2.8 M bookings",
    description: "Doorstep bike and car repair, snow foam wash, puncture repair, battery replacement, oil change and vehicle inspection",
    subServices: [
      {
        id: "veh-bike-rep",
        name: "Bike Repair",
        shortName: "Bike Repair",
        rating: 4.87,
        reviewsCount: "210K reviews",
        price: 299,
        duration: "45 mins",
        icon: "bi-bicycle",
        thumbnail: "https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Spark plug cleaning, carburetor tuning and accelerator cable lubricate",
          "Front/rear drum & disc brake pad inspection and tension adjustment",
          "Drive chain cleaning, slack adjustment and Motul chain lube spray"
        ],
        exclusions: ["Engine cylinder re-boring"],
        steps: ["Spark plug check", "Brake pad adjustment", "Chain cleaning & lube", "Test drive inspection"]
      },
      {
        id: "veh-car-rep",
        name: "Car Repair",
        shortName: "Car Repair",
        rating: 4.89,
        reviewsCount: "180K reviews",
        price: 599,
        duration: "60 mins",
        icon: "bi-car-front-fill",
        thumbnail: "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Brake pad replacement, disc rotor inspection and brake fluid top-up",
          "Alternator charging test, fan belt tension check and coolant flush",
          "Suspension bush inspection and steering linkage audit"
        ],
        exclusions: ["Major gearbox dismantling"],
        steps: ["OBD scanner scan", "Under-chassis inspection", "Brake & belt fix", "Engine road test"]
      },
      {
        id: "veh-bike-wash",
        name: "Bike Washing",
        shortName: "Bike Wash",
        rating: 4.86,
        reviewsCount: "195K reviews",
        price: 199,
        duration: "30 mins",
        icon: "bi-droplet-fill",
        thumbnail: "https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "High-pressure pressure jet water rinse to remove mud from underbody",
          "Active snow foam wash with microfibre mitt scrubbing",
          "Tyre shine dressing, chain lube spray and chrome mirror polish"
        ],
        exclusions: ["Full bike ceramic coating"],
        steps: ["Underbody water jet", "Snow foam mitt scrub", "Microfibre dry", "Tyre & chain polish"]
      },
      {
        id: "veh-car-wash",
        name: "Car Washing",
        shortName: "Car Foam Wash",
        rating: 4.90,
        reviewsCount: "310K reviews",
        price: 449,
        duration: "45 mins",
        icon: "bi-water",
        thumbnail: "https://images.unsplash.com/photo-1520340356584-f9917d1eea6f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Exterior dual-stage snow foam wash and pressure rinse",
          "Full interior vacuuming: seats, carpets, trunk and foot mats",
          "Dashboard dressing, glass cleaning and tyre rubber polish"
        ],
        exclusions: ["Seat upholstery extraction shampoo"],
        steps: ["Exterior foam soak", "Pressure rinse", "Interior vacuum", "Dashboard & tyre polish"]
      },
      {
        id: "veh-puncture",
        name: "Puncture Repair",
        shortName: "Puncture Fix",
        rating: 4.85,
        reviewsCount: "250K reviews",
        price: 149,
        duration: "20 mins",
        icon: "bi-circle",
        thumbnail: "https://images.unsplash.com/photo-1578844251758-2f71da64c96f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Doorstep tubeless tyre puncture repair using rubber mushroom plug",
          "Tyre valve leak test and rim bead inspection",
          "Air pressure inflation to OEM specified PSI"
        ],
        exclusions: ["Tyre sidewall slash repair"],
        steps: ["Leak bubble test", "Puncture reaming", "Mushroom plug seal", "Digital PSI inflation"]
      },
      {
        id: "veh-battery",
        name: "Battery Replacement",
        shortName: "Battery Replace",
        rating: 4.88,
        reviewsCount: "130K reviews",
        price: 249,
        duration: "30 mins",
        icon: "bi-battery-full",
        thumbnail: "https://images.unsplash.com/photo-1580273916550-e323be2ae537?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Doorstep delivery and installation of Exide/Amaron battery",
          "Battery terminal wire brushing, anti-corrosion spray and clamp tightening",
          "Alternator charging voltage diagnostic test"
        ],
        exclusions: ["Battery unit cost (paid to provider with warranty card)"],
        steps: ["Battery voltage test", "Terminal corrosion scrub", "New battery clamp", "Alternator check"]
      },
      {
        id: "veh-oil",
        name: "Oil Change",
        shortName: "Engine Oil Change",
        rating: 4.89,
        reviewsCount: "175K reviews",
        price: 249,
        duration: "30 mins",
        icon: "bi-fuel-pump",
        thumbnail: "https://images.unsplash.com/photo-1487754180451-c456f719a1fc?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Old engine oil draining and eco-friendly collection",
          "Oil filter replacement and sump plug washer change",
          "Fresh Castrol/Motul synthetic oil refill to maximum dipstick mark"
        ],
        exclusions: ["Cost of engine oil can"],
        steps: ["Sump drain plug open", "Oil filter replacement", "Fresh oil pour", "Dipstick level audit"]
      },
      {
        id: "veh-inspect",
        name: "Vehicle Inspection",
        shortName: "Vehicle Inspection",
        rating: 4.91,
        reviewsCount: "115K reviews",
        price: 499,
        duration: "45 mins",
        icon: "bi-clipboard-check",
        thumbnail: "https://images.unsplash.com/photo-1486006920555-c77dce18193b?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Comprehensive 40-point vehicle pre-purchase inspection",
          "Engine compression, gearbox shift smoothness and brake health audit",
          "Accidental damage check, rust assessment and digital report"
        ],
        exclusions: ["RTO document transfer paperwork"],
        steps: ["Engine diagnostic walkthrough", "Brake & suspension test", "Chassis paint meter test", "Digital health report"]
      }
    ],
    reviews: [
      { author: "Ajay Sharma", rating: 5, date: "Yesterday", comment: "Mobile car foam wash outside my apartment was faster and cleaner than going to a service station." },
      { author: "Rahul Verma", rating: 5, date: "4 days ago", comment: "Battery jump start and oil change done right in my garage. Exceptional service!" }
    ]
  },

  // 7. Laundry and Dry Cleaning
  "Laundry and Dry Cleaning": {
    categoryTitle: "Laundry and Dry Cleaning",
    icon: "bi-basket",
    rating: 4.86,
    totalBookings: "3.2 M bookings",
    description: "Doorstep pickup & delivery for everyday laundry, suits, designer sarees, quilts, curtains and sneakers",
    subServices: [
      {
        id: "laund-wash",
        name: "Clothes Washing",
        shortName: "Clothes Washing",
        rating: 4.85,
        reviewsCount: "290K reviews",
        price: 149,
        duration: "24 hrs",
        icon: "bi-basket2",
        thumbnail: "https://images.unsplash.com/photo-1545173168-9f1947eebb7f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Doorstep wash and fold for regular daily garments (up to 5 kg)",
          "Eco-friendly Surf Excel / Ariel detergent with fabric conditioner rinse",
          "Tumble drying with sanitized warm air and crisp fold packaging"
        ],
        exclusions: ["Heavy embroidery wedding lehengas"],
        steps: ["Doorstep pickup", "Color segregation", "Gentle wash & soften", "Fold & delivery"]
      },
      {
        id: "laund-iron",
        name: "Ironing",
        shortName: "Steam Ironing",
        rating: 4.88,
        reviewsCount: "340K reviews",
        price: 99,
        duration: "12 hrs",
        icon: "bi-steam",
        thumbnail: "https://images.unsplash.com/photo-1582735689369-4fe89db7114c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Heavy commercial vacuum steam press for formal shirts, trousers and kurtas",
          "Crisp razor-sharp creases with zero scorch or fabric shine marks",
          "Hanger packaging with dust cover polythene"
        ],
        exclusions: ["Stain removal wash"],
        steps: ["Fabric heat calibration", "Steam table press", "Crease alignment", "Hanger packaging"]
      },
      {
        id: "laund-dryclean",
        name: "Dry Cleaning",
        shortName: "Dry Cleaning",
        rating: 4.90,
        reviewsCount: "380K reviews",
        price: 249,
        duration: "48 hrs",
        icon: "bi-tag",
        thumbnail: "https://images.unsplash.com/photo-1517677208171-0bc6725a3e60?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Suits, blazers, tuxedos and sherwanis dry cleaned using hydrocarbon solvents",
          "Pre-spotting stain treatment on cuffs, collar and lapels",
          "Form finisher steam shaping and breathable garment bag packing"
        ],
        exclusions: ["Leather jacket re-dyeing"],
        steps: ["Garment tagging", "Stain pre-treatment", "Solvent dry cycle", "Steam shape finish"]
      },
      {
        id: "laund-blanket",
        name: "Blanket Cleaning",
        shortName: "Blanket Cleaning",
        rating: 4.87,
        reviewsCount: "210K reviews",
        price: 299,
        duration: "48 hrs",
        icon: "bi-square",
        thumbnail: "https://images.unsplash.com/photo-1584100936595-c0654b55a2e2?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Double mink blankets, heavy winter quilts and comforters deep wash",
          "Anti-bacterial sanitized drying to prevent damp winter mildew odor",
          "Aroma vacuum seal packaging for compact wardrobe storage"
        ],
        exclusions: ["Torn stitch repairs"],
        steps: ["Weight check & spot clean", "Heavy drum wash", "Tumble dry & fluff", "Aroma pack seal"]
      },
      {
        id: "laund-curtain",
        name: "Curtain Cleaning",
        shortName: "Curtain Clean",
        rating: 4.86,
        reviewsCount: "140K reviews",
        price: 249,
        duration: "48 hrs",
        icon: "bi-layout-split",
        thumbnail: "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Living room drapes, sheer curtains and blackout curtains dry cleaning",
          "Eyelet ring protection, dust extraction and pleat retention steaming",
          "Doorstep unhanging and re-hanging assistance available"
        ],
        exclusions: ["Rerodding damaged eyelets"],
        steps: ["Unhanging assistance", "Dry solvent wash", "Pleat press", "Clean delivery"]
      },
      {
        id: "laund-shoe",
        name: "Shoe Cleaning",
        shortName: "Shoe Cleaning",
        rating: 4.88,
        reviewsCount: "160K reviews",
        price: 199,
        duration: "24 hrs",
        icon: "bi-bag",
        thumbnail: "https://images.unsplash.com/photo-1549298916-b41d501d3772?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Sneakers, canvas shoes and leather loafers manual restoration",
          "Midsole stain scrubbing, shoelace brightening and deep deodorization",
          "Water-repellent protective coat spray"
        ],
        exclusions: ["Torn sole gluing"],
        steps: ["Dry brush dirt clean", "Sole foam scrub", "Inner deodorize", "Protective coat spray"]
      },
      {
        id: "laund-delivery",
        name: "Laundry Pickup and Delivery",
        shortName: "Pickup & Delivery",
        rating: 4.92,
        reviewsCount: "410K reviews",
        price: 49,
        duration: "Same day",
        icon: "bi-truck",
        thumbnail: "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Zero-effort doorstep pickup from your doorstep at your preferred time slot",
          "Itemized digital receipt with counts and garment notes",
          "Neatly packed, dust-proof drop-off back to your home"
        ],
        exclusions: ["Outstation courier"],
        steps: ["Slot confirmation", "Doorstep bag collection", "Digital count receipt", "Delivered on-time"]
      }
    ],
    reviews: [
      { author: "Swati Ghosh", rating: 5, date: "2 days ago", comment: "Sent 2 heavy mink blankets for dry clean. Delivered back fresh, fluffy and vacuum-sealed." },
      { author: "Tanmay Sen", rating: 5, date: "5 days ago", comment: "Crisp steam ironing with hangers. Perfect service for working professionals." }
    ]
  },

  // 8. Carpenter Services
  "Carpenter Services": {
    categoryTitle: "Carpenter Services",
    icon: "bi-hammer",
    rating: 4.87,
    totalBookings: "3.7 M bookings",
    description: "Skilled carpenters for furniture repairs, door lock fitting, bed assembly, wardrobe channels and modular woodwork",
    subServices: [
      {
        id: "carp-furn-rep",
        name: "Furniture Repair",
        shortName: "Furniture Repair",
        rating: 4.88,
        reviewsCount: "270K reviews",
        price: 249,
        duration: "40 mins",
        icon: "bi-tools",
        thumbnail: "https://images.unsplash.com/photo-1538688525198-9b88f6f53126?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Wobbly dining chair reinforcement, joint gluing with Fevicol SH and bracing",
          "Wooden table leg tightening, drawer slider repair and plywood touch-up",
          "Scratch buffing and wax polish application"
        ],
        exclusions: ["Full sofa upholstery re-cushioning"],
        steps: ["Joint inspection", "Adhesive & bracket brace", "Leveling test", "Protective polish touch"]
      },
      {
        id: "carp-door-rep",
        name: "Door Repair",
        shortName: "Door Repair",
        rating: 4.87,
        reviewsCount: "220K reviews",
        price: 199,
        duration: "30 mins",
        icon: "bi-door-closed",
        thumbnail: "https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Door bottom rubbing trim plane work to prevent floor scratches",
          "Hinge screw tightening, lubricating squeaky metal joints",
          "Latch, tower bolt and door stopper installation"
        ],
        exclusions: ["Door frame civil replacement"],
        steps: ["Clearance measurement", "Bottom plane shaving", "Hinge realignment", "Smooth swing check"]
      },
      {
        id: "carp-lock",
        name: "Lock Installation",
        shortName: "Lock Install",
        rating: 4.90,
        reviewsCount: "310K reviews",
        price: 299,
        duration: "45 mins",
        icon: "bi-lock",
        thumbnail: "https://images.unsplash.com/photo-1558002038-1055907df827?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Godrej mortise lock, deadbolt or cylinder latch precision chisel mortising",
          "Smart digital biometric fingerprint door lock mounting and strike plate fit",
          "Smooth key turn verification and magnetic latch adjustment"
        ],
        exclusions: ["Cost of lock hardware"],
        steps: ["Mortise hole marking", "Wood chisel cutting", "Lock mechanism install", "Key turn verification"]
      },
      {
        id: "carp-bed",
        name: "Bed Assembly",
        shortName: "Bed Assembly",
        rating: 4.89,
        reviewsCount: "240K reviews",
        price: 499,
        duration: "60 mins",
        icon: "bi-inboxes",
        thumbnail: "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "King/Queen size wooden hydraulic storage bed assembly",
          "Hydraulic gas pump bracket alignment, headboard attachment and slats",
          "Frame squaring and wobble-free stability guarantee"
        ],
        exclusions: ["Mattress cost"],
        steps: ["Frame perimeter layout", "Corner joint bolting", "Hydraulic pump bracket fix", "Weight test"]
      },
      {
        id: "carp-wardrobe",
        name: "Wardrobe Repair",
        shortName: "Wardrobe Repair",
        rating: 4.86,
        reviewsCount: "190K reviews",
        price: 299,
        duration: "40 mins",
        icon: "bi-grid-1x2",
        thumbnail: "https://images.unsplash.com/photo-1595428774223-ef52624120d2?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Sliding wardrobe top/bottom channel roller wheel alignment and replacement",
          "Hydraulic soft-close hinge replacement and door gap calibration",
          "Magnet catchers and drawer telescopic channel tuning"
        ],
        exclusions: ["Replacing full laminate doors"],
        steps: ["Roller wheel inspection", "Track realignment", "Soft-close hinge tune", "Smooth glide test"]
      },
      {
        id: "carp-shelf",
        name: "Shelf Installation",
        shortName: "Shelf Install",
        rating: 4.87,
        reviewsCount: "160K reviews",
        price: 249,
        duration: "35 mins",
        icon: "bi-bookshelf",
        thumbnail: "https://images.unsplash.com/photo-1594026112284-02bb6f3352fe?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Floating wall shelf bracket drilling with spirit level laser calibration",
          "Heavy duty rawl plugs and concealed metal supports",
          "Corner display shelf and book rack mounting"
        ],
        exclusions: ["Cost of decorative shelves"],
        steps: ["Laser level marking", "Hammer drill holes", "Concealed bracket fix", "Load stability test"]
      },
      {
        id: "carp-furn-asm",
        name: "Furniture Assembly",
        shortName: "Furniture Assembly",
        rating: 4.90,
        reviewsCount: "280K reviews",
        price: 399,
        duration: "50 mins",
        icon: "bi-box-seam",
        thumbnail: "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Precision assembly for IKEA, Pepperfry, Urban Ladder, Amazon flatpack units",
          "Study desks, computer tables, TV media units and shoe cabinets",
          "All minifix cam-lock fasteners tightened according to assembly manual"
        ],
        exclusions: ["Custom woodwork cutting"],
        steps: ["Part unboxing & audit", "Cam-lock assembly", "Wall anchor safety strap", "Inspection"]
      }
    ],
    reviews: [
      { author: "Binod Mahato", rating: 5, date: "3 days ago", comment: "Suraj assembled our hydraulic king bed in Morabadi in under 1 hour. Extremely skilled carpenter." },
      { author: "Ritu Kujur", rating: 5, date: "1 week ago", comment: "Godrej mortise lock installed with neat wood chisel finish. Very safe and sturdy." }
    ]
  },

  // 9. Painting Services
  "Painting Services": {
    categoryTitle: "Painting Services",
    icon: "bi-brush",
    rating: 4.88,
    totalBookings: "2.4 M bookings",
    description: "Interior & exterior wall painting, damp waterproofing, single room refresh, textures, putty smoothing and wood polish",
    subServices: [
      {
        id: "paint-fullhome",
        name: "Full Home Painting",
        shortName: "Full Home Painting",
        rating: 4.91,
        reviewsCount: "210K reviews",
        price: 4999,
        duration: "3-5 days",
        icon: "bi-house",
        thumbnail: "https://images.unsplash.com/photo-1589939705384-5185137a7f0f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Complete interior Asian Paints Royale Luxury / Tractor Emulsion 2-coat painting",
          "Furniture and flooring plastic sheet masking to prevent paint drops",
          "Wall sanding, 2 coats of Birla White putty, primer and post-paint clean"
        ],
        exclusions: ["Civil plaster rebuild"],
        steps: ["Furniture masking", "Putty & primer sanding", "2-coat emulsion roller", "Floor cleaning & unmasking"]
      },
      {
        id: "paint-singleroom",
        name: "Single Room Painting",
        shortName: "Single Room",
        rating: 4.87,
        reviewsCount: "140K reviews",
        price: 1499,
        duration: "1 day",
        icon: "bi-square-half",
        thumbnail: "https://images.unsplash.com/photo-1562259949-e8e7689d7828?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Quick 1-day transformation for bedroom, home office or study room",
          "Wall crack filling, minor putty touch-up and ceiling whitening",
          "2 coats of premium washable paint with roller application"
        ],
        exclusions: ["Waterproofing terrace slab"],
        steps: ["Floor sheet cover", "Crack repair & primer", "Wall color rolling", "Cleanup & handoff"]
      },
      {
        id: "paint-wall",
        name: "Wall Painting",
        shortName: "Wall Painting",
        rating: 4.88,
        reviewsCount: "180K reviews",
        price: 799,
        duration: "4 hrs",
        icon: "bi-paint-bucket",
        thumbnail: "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Single living room accent wall designer color or geometric stencil pattern",
          "Metallic Royale Play texture finish application by certified master painter",
          "Crisp masking tape edge borders and clean peel"
        ],
        exclusions: ["Full room repainting"],
        steps: ["Edge tape masking", "Base coat application", "Texture/stencil trowel", "Tape peel & buff"]
      },
      {
        id: "paint-doorwin",
        name: "Door and Window Painting",
        shortName: "Door & Window",
        rating: 4.85,
        reviewsCount: "110K reviews",
        price: 499,
        duration: "3 hrs",
        icon: "bi-door-open",
        thumbnail: "https://images.unsplash.com/photo-1533090161767-e6ffed986c88?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Synthetic enamel paint for wooden/metal doors, window grills and frames",
          "Rust scraping with wire brush and red oxide metal primer coat",
          "High gloss or satin finish application with zero brush marks"
        ],
        exclusions: ["Replacing rotted wood"],
        steps: ["Old paint scraping", "Rust inhibitor primer", "2-coat synthetic enamel", "Handle protection"]
      },
      {
        id: "paint-waterproof",
        name: "Waterproofing",
        shortName: "Waterproofing",
        rating: 4.90,
        reviewsCount: "195K reviews",
        price: 1999,
        duration: "1 day",
        icon: "bi-shield-check",
        thumbnail: "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Terrace, bathroom wall and balcony dampness chemical barrier treatment",
          "Dr. Fixit polymer modified slurry coat and elastomeric membrane",
          "Deep structural crack routing and fiber mesh reinforcement"
        ],
        exclusions: ["Full roof civil dismantling"],
        steps: ["Moisture meter check", "Crack routing & V-groove", "Polymer slurry coat", "Elastomeric topcoat"]
      },
      {
        id: "paint-putty",
        name: "Wall Putty and Repair",
        shortName: "Putty & Repair",
        rating: 4.86,
        reviewsCount: "125K reviews",
        price: 699,
        duration: "4 hrs",
        icon: "bi-tools",
        thumbnail: "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Plaster crack gouging, filling with joint compound and skim coating",
          "Birla White wall care putty smoothing with mechanical dry sander",
          "Water-based primer application creating a mirror-flat paint foundation"
        ],
        exclusions: ["Major structural column repairs"],
        steps: ["Loose paint scraping", "Deep crack putty fill", "Dual putty skim coat", "Zero-dust sanding"]
      }
    ],
    reviews: [
      { author: "Deepak Sahu", rating: 5, date: "3 days ago", comment: "Rohit and his painting crew did our 3BHK in Bariatu. Masked all furniture and finished on schedule!" },
      { author: "Anjali Gupta", rating: 5, date: "1 week ago", comment: "The damp waterproofing on our bedroom exterior wall worked miracles this monsoon." }
    ]
  },

  // 10. Pest Control
  "Pest Control": {
    categoryTitle: "Pest Control",
    icon: "bi-shield-shaded",
    rating: 4.90,
    totalBookings: "3.5 M bookings",
    description: "Government-licensed odorless cockroach gel baiting, termite chemical barrier, bed bug heat treatment and mosquito control",
    subServices: [
      {
        id: "pest-roach",
        name: "Cockroach Control",
        shortName: "Cockroach Control",
        rating: 4.91,
        reviewsCount: "410K reviews",
        price: 449,
        duration: "40 mins",
        icon: "bi-bug",
        thumbnail: "https://images.unsplash.com/photo-1628177142898-93e36e4e3a50?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Odorless Bayer Maxforce cockroach gel dots applied across cabinet hinges and sinks",
          "Drain sewer pipe larvicide spray to eliminate breeding nests",
          "Safe for infants, elderly and domestic pets with zero kitchen emptying needed"
        ],
        exclusions: ["Termite wood drilling"],
        steps: ["Infestation inspection", "Strategic gel baiting", "Drain pipe spray", "Preventive tips handoff"]
      },
      {
        id: "pest-termite",
        name: "Termite Control",
        shortName: "Termite Control",
        rating: 4.89,
        reviewsCount: "280K reviews",
        price: 999,
        duration: "90 mins",
        icon: "bi-shield-x",
        thumbnail: "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Drill-Fill-Seal method: chemical termiticide injection along skirting and doors",
          "Wooden door frames and wardrobe back panels oil-based anti-termite spray",
          "Holes sealed with matching color white cement with 1-year service warranty"
        ],
        exclusions: ["Rebuilding collapsed hollow wooden panels"],
        steps: ["Acoustic wood audit", "Drilling chemical holes", "Termiticide pressure injection", "Cement sealing"]
      },
      {
        id: "pest-mosquito",
        name: "Mosquito Control",
        shortName: "Mosquito Control",
        rating: 4.87,
        reviewsCount: "170K reviews",
        price: 399,
        duration: "30 mins",
        icon: "bi-cloud-haze",
        thumbnail: "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Thermal fogging in balcony, compound, garden and open drains",
          "Indoor residual surface spray on curtains and dark hiding corners",
          "Biological larvicide drops in standing water pots and AC trays"
        ],
        exclusions: ["Window mesh installation"],
        steps: ["Indoor corner spray", "Balcony thermal fogging", "Drain larvicide treatment", "30-min ventilate guide"]
      },
      {
        id: "pest-bedbug",
        name: "Bed Bug Treatment",
        shortName: "Bed Bug Treatment",
        rating: 4.90,
        reviewsCount: "260K reviews",
        price: 799,
        duration: "60 mins",
        icon: "bi-emoji-dizzy",
        thumbnail: "https://images.unsplash.com/photo-1631049307264-da0ec9d70304?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Two-visit intensive eradication protocol targeting adult bugs and newly hatched eggs",
          "High-temperature steam spray along mattress seams and bed headboards",
          "Micro-encapsulated insecticide spray inside electrical faceplates and cracks"
        ],
        exclusions: ["Discarding old infested pillows"],
        steps: ["Mattress inspection", "Seam steam treatment", "Chemical perimeter spray", "Follow-up visit day 15"]
      },
      {
        id: "pest-rat",
        name: "Rat Control",
        shortName: "Rat Control",
        rating: 4.85,
        reviewsCount: "135K reviews",
        price: 399,
        duration: "35 mins",
        icon: "bi-exclamation-triangle",
        thumbnail: "https://images.unsplash.com/photo-1581092160607-ee22621dd758?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Tamper-proof rodent bait stations placed along entry corridors and kitchen ducts",
          "Industrial sticky glue boards placed in false ceilings and pantry corners",
          "Complete audit of entry holes with wire mesh sealing recommendations"
        ],
        exclusions: ["Permanent civil masonry pipe sealing"],
        steps: ["Runway track inspection", "Bait box deployment", "Glue pad placement", "Audit report"]
      },
      {
        id: "pest-ant",
        name: "Ant Control",
        shortName: "Ant Control",
        rating: 4.86,
        reviewsCount: "150K reviews",
        price: 349,
        duration: "30 mins",
        icon: "bi-flower1",
        thumbnail: "https://images.unsplash.com/photo-1527515637462-cff94eecc1ac?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Herbal sweet bait granules that worker ants carry to queen colony",
          "Organic odorless barrier spray across window sills, door thresholds and slabs",
          "Wall crevice dusting to destroy indoor ant nesting"
        ],
        exclusions: ["Garden outdoor lawn treatment"],
        steps: ["Colony trace", "Granular baiting", "Threshold barrier spray", "Crevice dusting"]
      }
    ],
    reviews: [
      { author: "Santosh Verma", rating: 5, date: "Yesterday", comment: "Odorless cockroach gel baiting worked like magic in our kitchen. Not a single roach seen in 2 weeks." },
      { author: "Kavita Ekka", rating: 5, date: "4 days ago", comment: "Very polite technician. Carried warranty card and treated termite door frames safely." }
    ]
  },

  // 11. Moving and Shifting
  "Moving and Shifting": {
    categoryTitle: "Moving and Shifting",
    icon: "bi-truck",
    rating: 4.89,
    totalBookings: "2.1 M bookings",
    description: "Professional house and office shifting, furniture packing with multi-layer bubble wrap and local mini truck transport",
    subServices: [
      {
        id: "mov-home",
        name: "Home Shifting",
        shortName: "Home Shifting",
        rating: 4.90,
        reviewsCount: "220K reviews",
        price: 3499,
        duration: "4-6 hrs",
        icon: "bi-house-door-fill",
        thumbnail: "https://images.unsplash.com/photo-1600518464441-9154a4dea21b?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Complete 1/2/3 BHK household shifting by verified trained movers squad",
          "Dedicated covered transport vehicle (Tata 407 / Bolero Maxi Truck)",
          "Loading, transportation, safe unloading and placement in designated rooms"
        ],
        exclusions: ["Interstate toll taxes"],
        steps: ["Inventory survey", "Truck loading with straps", "Safe transport transit", "Room-by-room placement"]
      },
      {
        id: "mov-office",
        name: "Office Shifting",
        shortName: "Office Shifting",
        rating: 4.88,
        reviewsCount: "130K reviews",
        price: 4999,
        duration: "6-8 hrs",
        icon: "bi-building-fill",
        thumbnail: "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Corporate workstation, ergonomic chairs, desktop PCs and filing cabinet transport",
          "Anti-static bubble wrap for computer monitors and servers",
          "Numbered box labeling system ensuring zero loss of company documents"
        ],
        exclusions: ["IT network server configuration"],
        steps: ["Pre-move labeling", "Anti-static electronics wrap", "Heavy desk loading", "Destination unpack"]
      },
      {
        id: "mov-furniture",
        name: "Furniture Shifting",
        shortName: "Furniture Shifting",
        rating: 4.87,
        reviewsCount: "185K reviews",
        price: 1299,
        duration: "2 hrs",
        icon: "bi-box-seam",
        thumbnail: "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Single heavy item relocation: king bed, 3-seater sofa, almirah or refrigerator",
          "Edge foam guard protectors and cling film packaging",
          "Careful stairwell navigation and doorstep transit"
        ],
        exclusions: ["Dismantling motorized treadmills"],
        steps: ["Foam edge protection", "Strapped trolley transport", "Mini truck transit", "Unwrap in room"]
      },
      {
        id: "mov-pack",
        name: "Packing and Unpacking",
        shortName: "Packing & Unpacking",
        rating: 4.89,
        reviewsCount: "160K reviews",
        price: 999,
        duration: "3 hrs",
        icon: "bi-boxes",
        thumbnail: "https://images.unsplash.com/photo-1580674285054-bed31e145f59?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Multi-layer corrugated boxes, bubble wrap and tape packing of fragile crockery",
          "Clothes wardrobe carton boxing and mattress protective covers",
          "Destination unpacking, bubble wrap disposal and furniture dusting"
        ],
        exclusions: ["Cost of extra carton boxes beyond package limit"],
        steps: ["Fragile item bubble wrapping", "Carton taping & labeling", "Destination unbox", "Debris clearing"]
      },
      {
        id: "mov-transport",
        name: "Local Goods Transport",
        shortName: "Goods Transport",
        rating: 4.86,
        reviewsCount: "290K reviews",
        price: 699,
        duration: "1-2 hrs",
        icon: "bi-truck-flatbed",
        thumbnail: "https://images.unsplash.com/photo-1519003722824-194d4455a60c?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "On-demand Tata Ace / Piaggio Ape mini truck with verified commercial driver",
          "Real-time GPS tracking of goods throughout Ranchi city routes",
          "Assistance with tailgate loading and rope securing"
        ],
        exclusions: ["Unloading inside multi-storey without lift"],
        steps: ["Vehicle dispatch", "Tailgate rope securing", "Direct city transit", "Delivery signoff"]
      }
    ],
    reviews: [
      { author: "Dharmendra Prasad", rating: 5, date: "3 days ago", comment: "Shifted our 2BHK from Lalpur to Morabadi. Zero scratches on our glass dining table. Excellent movers!" },
      { author: "Nalini Soren", rating: 5, date: "1 week ago", comment: "Mini truck arrived in 15 minutes for sofa transport. Economical and punctual." }
    ]
  },

  // 12. Gardening and Home Maintenance
  "Gardening and Home Maintenance": {
    categoryTitle: "Gardening and Home Maintenance",
    icon: "bi-tree",
    rating: 4.88,
    totalBookings: "1.9 M bookings",
    description: "Experienced gardeners for potted plant care, lawn mowing, hedge trimming, plant repotting and general household handyman fixes",
    subServices: [
      {
        id: "gard-plant",
        name: "Plant Care",
        shortName: "Plant Care",
        rating: 4.89,
        reviewsCount: "175K reviews",
        price: 299,
        duration: "45 mins",
        icon: "bi-flower2",
        thumbnail: "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Potted plant inspection, yellow dead leaf pruning and pest wash with neem oil",
          "Soil aeration using hand trowel and vermicompost organic nutrition",
          "Watering schedule optimization and indoor plant sunshine repositioning"
        ],
        exclusions: ["Cost of exotic replacement plants"],
        steps: ["Plant health triage", "Dead leaf trimming", "Soil loosening & compost", "Neem oil mist"]
      },
      {
        id: "gard-maint",
        name: "Garden Maintenance",
        shortName: "Garden Maintenance",
        rating: 4.87,
        reviewsCount: "210K reviews",
        price: 499,
        duration: "90 mins",
        icon: "bi-tree-fill",
        thumbnail: "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Weed extraction, flower bed trenching and edge border cleaning",
          "Shrub and hedge shaping with manual shears into clean geometric lines",
          "Dead fallen leaves collection and compost bag disposal"
        ],
        exclusions: ["Felling 30-foot mature trees"],
        steps: ["Weed clearing", "Hedge shear shaping", "Flower bed dressing", "Garden rake cleanup"]
      },
      {
        id: "gard-lawn",
        name: "Lawn Mowing",
        shortName: "Lawn Mowing",
        rating: 4.88,
        reviewsCount: "145K reviews",
        price: 399,
        duration: "60 mins",
        icon: "bi-scissors",
        thumbnail: "https://images.unsplash.com/photo-1592417817098-8f3d69104a47?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Uniform lawn grass cutting using motor rotary lawnmower with catcher box",
          "Edge trimming along pathways, borders and tree bases with string trimmer",
          "Grass clipping collection and sweep-clean"
        ],
        exclusions: ["Lawn turf replacement"],
        steps: ["Lawn debris check", "Rotary mower run", "Path edge trimming", "Grass sweep & bagging"]
      },
      {
        id: "gard-repot",
        name: "Plant Repotting",
        shortName: "Plant Repotting",
        rating: 4.86,
        reviewsCount: "120K reviews",
        price: 249,
        duration: "30 mins",
        icon: "bi-inbox",
        thumbnail: "https://images.unsplash.com/photo-1485955900006-10f4d324d411?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Root-bound plant transfer to larger ceramic, clay or plastic pot",
          "Enriched potting mix formulation with coco peat, perlite and bone meal",
          "Root ball untangling, drainage hole pebble setup and firm watering"
        ],
        exclusions: ["Cost of ceramic pots"],
        steps: ["Pot drainage prep", "Root untangling", "Enriched potting soil fill", "Water soak & settle"]
      },
      {
        id: "gard-handyman",
        name: "General Home Maintenance",
        shortName: "Handyman Service",
        rating: 4.90,
        reviewsCount: "250K reviews",
        price: 349,
        duration: "60 mins",
        icon: "bi-wrench",
        thumbnail: "https://images.unsplash.com/photo-1581578731548-c64695cc6952?auto=format&fit=crop&w=600&q=80",
        videoUrl: "https://assets.mixkit.co/videos/preview/mixkit-hands-of-an-electrician-fixing-wires-42004-large.mp4",
        inclusions: [
          "Household handyman punch list: curtain rod mounting, mirror wall drilling",
          "Door handles, loose bolts, hanging frames and minor odd-job fixes",
          "Bathroom towel rod and kitchen accessory rack drill installations"
        ],
        exclusions: ["Major structural masonry"],
        steps: ["Punch list review", "Laser level markings", "Hammer drill installations", "Customer checklist signoff"]
      }
    ],
    reviews: [
      { author: "Ramu Mahto", rating: 5, date: "Yesterday", comment: "Garden looks completely refreshed. Handyman also fixed 3 curtain brackets on the same visit!" },
      { author: "Shalini Oraon", rating: 5, date: "5 days ago", comment: "Repotted 8 indoor monstera and snake plants with nutrient soil. Very professional." }
    ]
  }
};

// Aliases mapping for common names, alternate casing, and legacy labels
SUB_SERVICES_DATA["Electrician"] = SUB_SERVICES_DATA["Electrician Services"];
SUB_SERVICES_DATA["Plumbing"] = SUB_SERVICES_DATA["Plumbing Services"];
SUB_SERVICES_DATA["Plumber"] = SUB_SERVICES_DATA["Plumbing Services"];
SUB_SERVICES_DATA["Home cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Home Cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Cleaner"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Bathroom cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Bathroom Cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Kitchen cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["Kitchen Cleaning"] = SUB_SERVICES_DATA["Cleaning Services"];
SUB_SERVICES_DATA["AC repair and servicing"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["AC Repair & Servicing"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["AC Repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Appliance repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Appliance Repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["RO and water purifier repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["RO & Water Purifier"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Washing machine repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Washing Machine Repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Refrigerator repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Refrigerator Repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Geyser repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Geyser Repair"] = SUB_SERVICES_DATA["AC and Appliance Repair"];
SUB_SERVICES_DATA["Salon at home"] = SUB_SERVICES_DATA["Salon and Grooming"];
SUB_SERVICES_DATA["Barber"] = SUB_SERVICES_DATA["Salon and Grooming"];
SUB_SERVICES_DATA["Salon"] = SUB_SERVICES_DATA["Salon and Grooming"];
SUB_SERVICES_DATA["Salon & Grooming"] = SUB_SERVICES_DATA["Salon and Grooming"];
SUB_SERVICES_DATA["Carpentry"] = SUB_SERVICES_DATA["Carpenter Services"];
SUB_SERVICES_DATA["Carpenter"] = SUB_SERVICES_DATA["Carpenter Services"];
SUB_SERVICES_DATA["Furniture assembly"] = SUB_SERVICES_DATA["Carpenter Services"];
SUB_SERVICES_DATA["Furniture Assembly"] = SUB_SERVICES_DATA["Carpenter Services"];
SUB_SERVICES_DATA["Painting"] = SUB_SERVICES_DATA["Painting Services"];
SUB_SERVICES_DATA["Painter"] = SUB_SERVICES_DATA["Painting Services"];
SUB_SERVICES_DATA["Pest control"] = SUB_SERVICES_DATA["Pest Control"];
SUB_SERVICES_DATA["CCTV installation"] = SUB_SERVICES_DATA["Electrician Services"];
SUB_SERVICES_DATA["CCTV Installation"] = SUB_SERVICES_DATA["Electrician Services"];
SUB_SERVICES_DATA["Mechanic"] = SUB_SERVICES_DATA["Vehicle Services"];

/**
 * Get Sub-Services for a given Service name or ID with intelligent fuzzy fallback
 */
function getSubServicesData(serviceName) {
  if (!serviceName) return SUB_SERVICES_DATA["Cleaning Services"];

  // Exact match
  if (SUB_SERVICES_DATA[serviceName]) {
    return SUB_SERVICES_DATA[serviceName];
  }

  // Case-insensitive / partial match
  const lower = serviceName.toLowerCase().trim();
  for (const [key, val] of Object.entries(SUB_SERVICES_DATA)) {
    if (key.toLowerCase() === lower) {
      return val;
    }
  }

  for (const [key, val] of Object.entries(SUB_SERVICES_DATA)) {
    if (key.toLowerCase().includes(lower) || lower.includes(key.toLowerCase())) {
      return val;
    }
  }

  // Fallback to Cleaning Services
  return SUB_SERVICES_DATA["Cleaning Services"] || Object.values(SUB_SERVICES_DATA)[0];
}
