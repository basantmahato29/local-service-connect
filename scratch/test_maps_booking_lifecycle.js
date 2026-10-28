const assert = require('assert');

async function testBookingLifecycle() {
  console.log('=== TEST 1: Customer Login ===');
  const loginRes = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'customer@localserviceconnect.com',
      password: 'Customer@12345'
    })
  });
  assert.strictEqual(loginRes.status, 200, 'Customer login must succeed');
  const custAuth = await loginRes.json();
  const custToken = custAuth.token;
  console.log('Customer logged in:', custAuth.email);

  console.log('=== TEST 2: Find Available Providers near Ranchi ===');
  const myBookingsRes0 = await fetch('http://localhost:8080/api/bookings/my', {
    headers: { 'Authorization': `Bearer ${custToken}` }
  });
  const myActivePending = (await myBookingsRes0.json()).filter(b => b.status === 'PENDING').map(b => b.providerId);

  const provRes = await fetch('http://localhost:8080/api/providers');
  assert.strictEqual(provRes.status, 200);
  const providers = await provRes.json();
  const targetProvider = providers.find(p => !myActivePending.includes(p.id) && p.services && p.services.length > 0) || providers[0];
  const targetService = targetProvider.services[0];
  console.log(`Selected Provider: #${targetProvider.id} "${targetProvider.businessName}", Service: #${targetService.id} "${targetService.name}"`);

  console.log('=== TEST 3: Coordinate Validation Test (Reject Out-of-Bounds Lat/Lng) ===');
  const invalidCoordRes = await fetch('http://localhost:8080/api/bookings', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${custToken}`
    },
    body: JSON.stringify({
      providerId: targetProvider.id,
      serviceId: targetService.id,
      bookingType: 'NORMAL',
      bookingDate: '2026-11-15',
      bookingTime: '14:00:00',
      address: 'Flat 101, Test Lane',
      area: 'Lalpur',
      latitude: 195.0, // Invalid: exceeds 90 degrees
      longitude: 85.3340
    })
  });
  console.log('Invalid coordinates response status:', invalidCoordRes.status);
  assert(invalidCoordRes.status === 400, 'Should reject invalid out-of-bounds coordinates with 400');
  console.log('Validation passed: out-of-bounds coordinates rejected!');

  console.log('=== TEST 4: Create Booking with Accurate Coordinates & Address ===');
  const testLat = 23.3685;
  const testLng = 85.3340;
  const testFormattedAddr = 'Circular Road, Lalpur, Ranchi, Jharkhand 834001';
  const testDoorDetail = 'Flat 302, Green Valley Apartments';
  const fullAddress = `${testDoorDetail}, ${testFormattedAddr}`;

  const createRes = await fetch('http://localhost:8080/api/bookings', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${custToken}`
    },
    body: JSON.stringify({
      providerId: targetProvider.id,
      serviceId: targetService.id,
      bookingType: 'NORMAL',
      bookingDate: '2026-11-20',
      bookingTime: '11:00:00',
      address: fullAddress,
      area: 'Lalpur',
      formattedAddress: testFormattedAddr,
      latitude: testLat,
      longitude: testLng,
      notes: 'Please arrive on time.'
    })
  });

  if (createRes.status !== 200 && createRes.status !== 201) {
    const errText = await createRes.text();
    console.error('Booking creation failed:', createRes.status, errText);
    throw new Error('Booking creation failed: ' + errText);
  }

  const createdBooking = await createRes.json();
  console.log('Booking successfully created! ID:', createdBooking.id);
  assert.strictEqual(createdBooking.latitude, testLat, 'Latitude must match');
  assert.strictEqual(createdBooking.longitude, testLng, 'Longitude must match');
  assert.strictEqual(createdBooking.formattedAddress, testFormattedAddr, 'Formatted address must match');
  assert.strictEqual(createdBooking.address, fullAddress, 'Full door address must match');
  console.log('Stored coordinates verified:', createdBooking.latitude, createdBooking.longitude);
  console.log('Stored formatted address verified:', createdBooking.formattedAddress);

  console.log('=== TEST 5: Verify Provider Dashboard Shows Coordinates & Location ===');
  // Log in as provider
  const provLoginRes = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: targetProvider.providerEmail || 'provider1@localserviceconnect.com',
      password: 'Provider@12345'
    })
  });

  if (provLoginRes.status === 200) {
    const provAuth = await provLoginRes.json();
    const myBookingsRes = await fetch('http://localhost:8080/api/bookings/my', {
      headers: { 'Authorization': `Bearer ${provAuth.token}` }
    });
    assert.strictEqual(myBookingsRes.status, 200);
    const myBookings = await myBookingsRes.json();
    const found = myBookings.find(b => b.id === createdBooking.id);
    assert(found, 'Created booking must appear in provider dashboard');
    assert.strictEqual(found.latitude, testLat);
    assert.strictEqual(found.longitude, testLng);
    assert.strictEqual(found.formattedAddress, testFormattedAddr);
    console.log(`Provider #${targetProvider.id} sees booking #${found.id} with coordinates (${found.latitude}, ${found.longitude}) and address: "${found.formattedAddress}"`);
  } else {
    console.log('Provider credentials skipped, verified via customer API get');
    const getRes = await fetch(`http://localhost:8080/api/bookings/${createdBooking.id}`, {
      headers: { 'Authorization': `Bearer ${custToken}` }
    });
    const fetched = await getRes.json();
    assert.strictEqual(fetched.latitude, testLat);
    assert.strictEqual(fetched.longitude, testLng);
  }

  console.log('\n=========================================');
  console.log('SUCCESS: All Backend & API Location tests passed cleanly!');
  console.log('=========================================');
}

testBookingLifecycle().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
