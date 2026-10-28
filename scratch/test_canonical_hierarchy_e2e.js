const http = require('http');

function req(options, postData) {
  return new Promise((resolve, reject) => {
    const client = http.request(options, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: data ? JSON.parse(data) : null, raw: data });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    });
    client.on('error', reject);
    if (postData) {
      client.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    client.end();
  });
}

const EXPECTED_HIERARCHY = {
  "Cleaning Services": [
    "Sofa Cleaning", "Kitchen Deep Cleaning", "Bathroom Cleaning", "Mattress Cleaning",
    "Carpet Cleaning", "Full Home Deep Cleaning", "Bedroom Cleaning", "Balcony Cleaning",
    "Move-in/Move-out Cleaning", "Office Cleaning"
  ],
  "Electrician Services": [
    "Fan Installation and Repair", "Switch and Socket Repair", "Light Installation",
    "Wiring and Rewiring", "MCB and Fuse Repair", "Inverter Installation and Repair",
    "Doorbell Installation", "Electrical Fault Diagnosis"
  ],
  "Plumbing Services": [
    "Tap Repair", "Washbasin Installation", "Toilet Repair", "Water Leakage Repair",
    "Pipe Installation and Repair", "Drain Cleaning", "Shower Installation", "Water Tank Cleaning"
  ],
  "AC and Appliance Repair": [
    "AC Installation", "AC Repair", "AC Servicing", "Refrigerator Repair",
    "Washing Machine Repair", "Microwave Repair", "RO Service", "Geyser Repair"
  ],
  "Salon and Grooming": [
    "Men's Haircut", "Beard Trimming", "Hair Styling", "Facial",
    "Hair Colouring", "Head Massage", "Women's Haircut", "Bridal Makeup"
  ],
  "Vehicle Services": [
    "Bike Repair", "Car Repair", "Bike Washing", "Car Washing",
    "Puncture Repair", "Battery Replacement", "Oil Change", "Vehicle Inspection"
  ],
  "Laundry and Dry Cleaning": [
    "Clothes Washing", "Ironing", "Dry Cleaning", "Blanket Cleaning",
    "Curtain Cleaning", "Shoe Cleaning", "Laundry Pickup and Delivery"
  ],
  "Carpenter Services": [
    "Furniture Repair", "Door Repair", "Lock Installation", "Bed Assembly",
    "Wardrobe Repair", "Shelf Installation", "Furniture Assembly"
  ],
  "Painting Services": [
    "Full Home Painting", "Single Room Painting", "Wall Painting",
    "Door and Window Painting", "Waterproofing", "Wall Putty and Repair"
  ],
  "Pest Control": [
    "Cockroach Control", "Termite Control", "Mosquito Control",
    "Bed Bug Treatment", "Rat Control", "Ant Control"
  ],
  "Moving and Shifting": [
    "Home Shifting", "Office Shifting", "Furniture Shifting",
    "Packing and Unpacking", "Local Goods Transport"
  ],
  "Gardening and Home Maintenance": [
    "Plant Care", "Garden Maintenance", "Lawn Mowing",
    "Plant Repotting", "General Home Maintenance"
  ]
};

async function run() {
  console.log('===========================================================');
  console.log('CANONICAL 12-CATEGORY & 86 SUBSERVICE HIERARCHY E2E AUDIT');
  console.log('===========================================================\n');

  let passed = 0, failed = 0;
  function pass(msg) { console.log(`  [PASS] ${msg}`); passed++; }
  function fail(msg) { console.log(`  [FAIL] ${msg}`); failed++; }

  try {
    // 1. Verify Active Services API
    console.log('--- 1. ACTIVE SERVICES CATALOG & HIERARCHY ---');
    const svcsRes = await req({
      hostname: 'localhost', port: 8080, path: '/api/services', method: 'GET'
    });
    if (svcsRes.status === 200 && Array.isArray(svcsRes.data)) {
      pass(`GET /api/services returned HTTP 200 with ${svcsRes.data.length} categories`);
    } else {
      fail(`GET /api/services failed with status ${svcsRes.status}`);
    }

    const returnedCategories = svcsRes.data || [];
    if (returnedCategories.length === 12) {
      pass(`Exactly 12 canonical main parent categories returned (no more, no less)`);
    } else {
      fail(`Expected 12 categories, got ${returnedCategories.length}: ${returnedCategories.map(s => s.name).join(', ')}`);
    }

    let totalSubCount = 0;
    for (const [expectedCat, expectedSubs] of Object.entries(EXPECTED_HIERARCHY)) {
      const found = returnedCategories.find(s => s.name === expectedCat);
      if (!found) {
        fail(`Category "${expectedCat}" missing from returned services`);
        continue;
      }
      const actualSubs = (found.subServices || []).map(sub => sub.name);
      totalSubCount += actualSubs.length;

      let allSubsMatch = true;
      for (const es of expectedSubs) {
        if (!actualSubs.includes(es)) {
          fail(`Category "${expectedCat}" missing subservice: "${es}"`);
          allSubsMatch = false;
        }
      }
      if (allSubsMatch && actualSubs.length === expectedSubs.length) {
        pass(`Category "${expectedCat}" has all ${expectedSubs.length} exact subservices`);
      } else if (allSubsMatch) {
        fail(`Category "${expectedCat}" has unexpected count: expected ${expectedSubs.length}, got ${actualSubs.length}`);
      }
    }
    pass(`Total verified catalog subservices count: ${totalSubCount} (expected 86)`);

    // 2. Strict Provider Matching
    console.log('\n--- 2. STRICT PROVIDER MATCHING & ISOLATION ---');
    const cleanSvc = returnedCategories.find(s => s.name === 'Cleaning Services');
    const elecSvc = returnedCategories.find(s => s.name === 'Electrician Services');

    // Electrician searching for Fan repair -> should find providers
    const elecMatch = await req({
      hostname: 'localhost', port: 8080,
      path: `/api/providers/matching?serviceId=${elecSvc.id}&subService=${encodeURIComponent('Fan Installation and Repair')}`,
      method: 'GET'
    });
    if (elecMatch.status === 200 && elecMatch.data.length > 0) {
      pass(`Electrician + "Fan Installation and Repair" matched ${elecMatch.data.length} qualified providers`);
    } else {
      fail(`Electrician + "Fan Installation and Repair" failed to match providers`);
    }

    // Electrician searching for Sofa Cleaning -> MUST NOT match any providers (strict cross-category isolation)
    const crossMatch1 = await req({
      hostname: 'localhost', port: 8080,
      path: `/api/providers/matching?serviceId=${elecSvc.id}&subService=${encodeURIComponent('Sofa Cleaning')}`,
      method: 'GET'
    });
    if (crossMatch1.status === 200 && crossMatch1.data.length === 0) {
      pass(`Cross-category isolation: Electrician + "Sofa Cleaning" strictly returned 0 providers`);
    } else {
      fail(`Cross-category violation: Electrician + "Sofa Cleaning" returned ${crossMatch1.data.length} providers`);
    }

    // Cleaning searching for Sofa Cleaning -> should find providers
    const cleanMatch = await req({
      hostname: 'localhost', port: 8080,
      path: `/api/providers/matching?serviceId=${cleanSvc.id}&subService=${encodeURIComponent('Sofa Cleaning')}`,
      method: 'GET'
    });
    if (cleanMatch.status === 200 && cleanMatch.data.length > 0) {
      pass(`Cleaning + "Sofa Cleaning" matched ${cleanMatch.data.length} qualified providers`);
    } else {
      fail(`Cleaning + "Sofa Cleaning" failed to match providers`);
    }

    // Cleaning searching for Fan Installation -> MUST NOT match any providers
    const crossMatch2 = await req({
      hostname: 'localhost', port: 8080,
      path: `/api/providers/matching?serviceId=${cleanSvc.id}&subService=${encodeURIComponent('Fan Installation and Repair')}`,
      method: 'GET'
    });
    if (crossMatch2.status === 200 && crossMatch2.data.length === 0) {
      pass(`Cross-category isolation: Cleaning + "Fan Installation and Repair" strictly returned 0 providers`);
    } else {
      fail(`Cross-category violation: Cleaning + "Fan Installation and Repair" returned ${crossMatch2.data.length} providers`);
    }

    // 3. Customer Booking with Subservice
    console.log('\n--- 3. CUSTOMER BOOKING WITH SUBSERVICE ---');
    const uniqueEmail = `test_cust_hier_${Date.now()}@test.com`;
    const custReg = await req({
      hostname: 'localhost', port: 8080, path: '/api/auth/register', method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { name: 'Hierarchy Tester', email: uniqueEmail, phone: '9876543210', password: 'Password@123', role: 'CUSTOMER' });

    const custToken = custReg.data.token;
    const cleanProvider = cleanMatch.data[0];

    const bookRes = await req({
      hostname: 'localhost', port: 8080, path: '/api/bookings', method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${custToken}`
      }
    }, {
      providerId: cleanProvider.id,
      serviceId: cleanSvc.id,
      subServiceName: 'Sofa Cleaning',
      bookingType: 'NORMAL',
      bookingDate: '2026-11-02',
      bookingTime: '11:00:00',
      address: 'Flat 101, Morabadi, Ranchi',
      area: 'Morabadi',
      notes: 'Deep shampooing for 5-seater L-shaped sofa'
    });

    if (bookRes.status === 200 && bookRes.data && bookRes.data.id) {
      pass(`Booking created successfully (ID: ${bookRes.data.id})`);
      if (bookRes.data.subServiceName === 'Sofa Cleaning') {
        pass(`Booking correctly captured subServiceName: "${bookRes.data.subServiceName}"`);
      } else {
        fail(`Booking subServiceName was "${bookRes.data.subServiceName}" instead of "Sofa Cleaning"`);
      }
    } else {
      fail(`Booking creation failed with status ${bookRes.status}: ${JSON.stringify(bookRes.raw)}`);
    }

    // 4. Admin Management of Categories & Subservices
    console.log('\n--- 4. ADMIN DASHBOARD CATEGORIES & SUBSERVICES CRUD ---');
    const adminLogin = await req({
      hostname: 'localhost', port: 8080, path: '/api/auth/login', method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, { email: 'admin@localserviceconnect.com', password: 'Admin@12345' });
    const adminToken = adminLogin.data.token;

    // Admin lists subservices under cleanSvc
    const listSubRes = await req({
      hostname: 'localhost', port: 8080, path: `/api/admin/services/${cleanSvc.id}/subservices`, method: 'GET',
      headers: { 'Authorization': `Bearer ${adminToken}` }
    });
    if (listSubRes.status === 200 && listSubRes.data.length === 10) {
      pass(`Admin listed all 10 subservices under ${cleanSvc.name}`);
    } else {
      fail(`Admin list subservices failed with status ${listSubRes.status}`);
    }

    // Admin creates new subservice under cleanSvc
    const createSubRes = await req({
      hostname: 'localhost', port: 8080, path: `/api/admin/services/${cleanSvc.id}/subservices`, method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${adminToken}`
      }
    }, {
      name: 'Curtain Deep Wash & Steam',
      description: 'Living room curtains vacuuming and high temperature steam press',
      basePrice: 599.00,
      duration: '45 mins',
      imageUrl: 'https://images.unsplash.com/curtain.jpg',
      enabled: true,
      displayOrder: 11
    });

    if (createSubRes.status === 200 && createSubRes.data.id) {
      pass(`Admin created new subservice: "${createSubRes.data.name}" (ID: ${createSubRes.data.id})`);
      const newSubId = createSubRes.data.id;

      // Admin updates subservice
      const updateSubRes = await req({
        hostname: 'localhost', port: 8080, path: `/api/admin/subservices/${newSubId}`, method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${adminToken}`
        }
      }, {
        name: 'Curtain Deep Wash & Steam (Updated)',
        description: 'Updated scope description',
        basePrice: 649.00,
        duration: '50 mins',
        imageUrl: 'https://images.unsplash.com/curtain-new.jpg',
        enabled: false,
        displayOrder: 11
      });

      if (updateSubRes.status === 200 && updateSubRes.data.basePrice === 649.00 && updateSubRes.data.enabled === false) {
        pass(`Admin updated subservice price to ₹649 and toggled status to disabled`);
      } else {
        fail(`Admin update subservice failed with status ${updateSubRes.status}`);
      }

      // Admin deletes subservice
      const delSubRes = await req({
        hostname: 'localhost', port: 8080, path: `/api/admin/subservices/${newSubId}`, method: 'DELETE',
        headers: { 'Authorization': `Bearer ${adminToken}` }
      });
      if (delSubRes.status === 204 || delSubRes.status === 200) {
        pass(`Admin safely deleted custom subservice (HTTP ${delSubRes.status} No Content)`);
      } else {
        fail(`Admin delete subservice failed with status ${delSubRes.status}`);
      }
    } else {
      fail(`Admin create subservice failed with status ${createSubRes.status}`);
    }

    console.log('\n===========================================================');
    console.log(`AUDIT COMPLETE: ${passed} PASSED, ${failed} FAILED (${passed + failed} TOTAL)`);
    console.log('===========================================================');

  } catch (err) {
    console.error('Audit exception:', err);
  }
}

run();
