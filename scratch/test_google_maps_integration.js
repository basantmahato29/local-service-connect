const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');

const BASE_URL = 'http://localhost:8080';
const FRONTEND_DIR = path.join(__dirname, '..', 'frontend');
const EXPECTED_KEY = 'AIzaSyBRakoPoEQSk_3loQwT0yK2mh56bjmWKfs';

function assert(condition, message) {
  if (!condition) {
    console.error(`  [FAIL] ${message}`);
    process.exit(1);
  }
  console.log(`  [PASS] ${message}`);
}

function fetchHttp(urlPath, options = {}) {
  return new Promise((resolve, reject) => {
    const isHttps = urlPath.startsWith('https://');
    const lib = isHttps ? https : http;
    const url = urlPath.startsWith('http') ? urlPath : (BASE_URL + urlPath);
    const req = lib.request(url, options, res => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch {}
        resolve({ status: res.statusCode, body: data, json, headers: res.headers });
      });
    });
    req.on('error', reject);
    if (options.body) {
      req.write(typeof options.body === 'string' ? options.body : JSON.stringify(options.body));
    }
    req.end();
  });
}

async function runMapsTests() {
  console.log('====================================================');
  console.log('GOOGLE MAPS INTEGRATION & END-TO-END VERIFICATION');
  console.log('====================================================\n');

  // 1. Verify Configuration Endpoint & Key
  console.log('1. Checking /api/config/maps endpoint...');
  const configRes = await fetchHttp('/api/config/maps');
  assert(configRes.status === 200, 'Config endpoint returned HTTP 200');
  assert(configRes.json && configRes.json.configured === true, 'Config endpoint confirms configured: true');
  assert(configRes.json && configRes.json.apiKey === EXPECTED_KEY, 'API key matches user provided key: ' + EXPECTED_KEY);
  assert(configRes.json.defaultLat === 23.3441, 'Default latitude is 23.3441');
  assert(configRes.json.defaultLng === 85.3096, 'Default longitude is 85.3096');

  // 2. Verify Google Maps JS API script loads with key
  console.log('\n2. Testing Google Maps Platform script delivery with key...');
  const scriptUrl = `https://maps.googleapis.com/maps/api/js?key=${EXPECTED_KEY}&libraries=places`;
  const scriptRes = await fetchHttp(scriptUrl);
  assert(scriptRes.status === 200, 'Google Maps JS API script returns HTTP 200');
  assert(scriptRes.body.includes('google.maps'), 'Script contains Google Maps engine definitions');

  // 3. Inspect Environment & Security Files
  console.log('\n3. Inspecting environment files & Git security...');
  const rootEnvPath = path.join(__dirname, '..', '.env');
  const rootExamplePath = path.join(__dirname, '..', '.env.example');
  const gitignorePath = path.join(__dirname, '..', '.gitignore');

  assert(fs.existsSync(rootEnvPath), '.env file exists in project root');
  assert(fs.readFileSync(rootEnvPath, 'utf-8').includes(EXPECTED_KEY), '.env contains the user Google Maps API key');

  assert(fs.existsSync(rootExamplePath), '.env.example exists');
  assert(fs.readFileSync(rootExamplePath, 'utf-8').includes('YOUR_GOOGLE_MAPS_API_KEY'), '.env.example uses placeholder without exposing key');

  assert(fs.existsSync(gitignorePath), '.gitignore exists');
  const gitignoreContent = fs.readFileSync(gitignorePath, 'utf-8');
  assert(gitignoreContent.includes('.env'), '.gitignore properly excludes .env');
  assert(!gitignoreContent.includes(EXPECTED_KEY), '.gitignore does not contain the key');

  // 4. Verify Frontend Maps Utility (maps.js)
  console.log('\n4. Inspecting frontend/js/maps.js central utility...');
  const mapsJs = fs.readFileSync(path.join(FRONTEND_DIR, 'js', 'maps.js'), 'utf-8');
  assert(mapsJs.includes('loadGoogleMaps'), 'LSC_MAPS exports loadGoogleMaps');
  assert(mapsJs.includes('loadGoogleMapsApi'), 'LSC_MAPS exports loadGoogleMapsApi alias');
  assert(mapsJs.includes('setupPlacesAutocomplete'), 'LSC_MAPS exports setupPlacesAutocomplete');
  assert(mapsJs.includes('attachPlacesAutocomplete'), 'LSC_MAPS exports attachPlacesAutocomplete alias');
  assert(mapsJs.includes('createLocationPickerMap'), 'LSC_MAPS exports createLocationPickerMap');
  assert(mapsJs.includes('createProvidersOverviewMap'), 'LSC_MAPS exports createProvidersOverviewMap');
  assert(mapsJs.includes('reverseGeocode'), 'LSC_MAPS exports reverseGeocode');
  assert(mapsJs.includes('getCurrentLocation'), 'LSC_MAPS exports getCurrentLocation');
  assert(mapsJs.includes('existingScript'), 'LSC_MAPS guards against duplicate script loading');

  // 5. Verify Pages that use Maps
  console.log('\n5. Verifying pages with interactive maps integration...');
  const pagesWithMaps = [
    { file: 'index.html', desc: 'Homepage live location search & autocomplete' },
    { file: 'providers.html', desc: 'Find providers interactive map & location picker' },
    { file: 'booking.html', desc: 'Doorstep booking location picker & address map' },
    { file: 'my-account.html', desc: 'Customer saved addresses with Google Maps pin & GPS' },
    { file: 'provider-dashboard.html', desc: 'Provider service area coverage circle & dispatch radius' }
  ];

  for (const p of pagesWithMaps) {
    const content = fs.readFileSync(path.join(FRONTEND_DIR, p.file), 'utf-8');
    assert(content.includes('/js/maps.js'), `${p.file} includes /js/maps.js`);
    assert(content.includes('LSC_MAPS'), `${p.file} invokes LSC_MAPS for ${p.desc}`);
  }

  // 6. Test Customer Address Management API with GPS Coordinates
  console.log('\n6. Testing Customer Address Persistence with Geolocation...');
  const timestamp = Date.now();
  const customerEmail = `map_cust_${timestamp}@test.com`;
  
  // Register customer
  const regRes = await fetchHttp('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: { name: 'Map Customer', email: customerEmail, password: 'Password@123', role: 'CUSTOMER', phone: '9876543210' }
  });
  assert(regRes.status === 200 || regRes.status === 201, 'Customer registered successfully');
  const token = regRes.json.token;
  assert(token, 'Customer received authentication token');

  // Save address with coordinates on customer profile
  const addrRes = await fetchHttp('/api/customer/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
    body: {
      address: 'Flat 402, Sunshine Heights, Circular Road',
      area: 'Lalpur',
      city: 'Ranchi',
      pincode: '834001',
      latitude: 23.3685,
      longitude: 85.3340,
      formattedAddress: 'Lalpur, Ranchi, Jharkhand, India'
    }
  });
  assert(addrRes.status === 200, 'Customer address and geolocation saved successfully via /api/customer/profile');
  assert(addrRes.json.latitude === 23.3685, 'Saved customer profile contains accurate latitude (23.3685)');
  assert(addrRes.json.longitude === 85.3340, 'Saved customer profile contains accurate longitude (85.3340)');
  assert(addrRes.json.area === 'Lalpur', 'Saved customer profile contains area: Lalpur');

  // 7. Test Provider Location & Service Area Persistence
  console.log('\n7. Testing Provider Coverage Location & Dispatch Radius Persistence...');
  const provEmail = `map_prov_${timestamp}@test.com`;
  const provReg = await fetchHttp('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: {
      name: 'Map Provider',
      email: provEmail,
      password: 'Password@123',
      role: 'PROVIDER',
      phone: `98711${String(timestamp).slice(-5)}`,
      businessName: 'Ranchi Rapid Repairs'
    }
  });
  assert(provReg.status === 200 || provReg.status === 201, 'Provider registered successfully');
  const provToken = provReg.json.token;

  // Update provider location and coverage radius
  const provProfRes = await fetchHttp('/api/provider/profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${provToken}` },
    body: {
      businessName: 'Ranchi Rapid Repairs',
      latitude: 23.3685,
      longitude: 85.3340,
      formattedAddress: 'Lalpur Chowk, Ranchi, Jharkhand',
      serviceRadiusKm: 15.0,
      city: 'Ranchi'
    }
  });
  assert(provProfRes.status === 200, 'Provider location and 15km service radius updated successfully');
  assert(provProfRes.json.latitude === 23.3685, 'Provider profile latitude matches map selection');
  assert(provProfRes.json.longitude === 85.3340, 'Provider profile longitude matches map selection');
  assert(provProfRes.json.serviceRadiusKm === 15.0, 'Provider coverage radius set to 15.0 km');

  // 8. Test Proximity Search Algorithm with Haversine distance
  console.log('\n8. Testing Proximity Search Endpoint (/api/providers/nearby)...');
  const nearbyRes = await fetchHttp('/api/providers/nearby?latitude=23.3685&longitude=85.3340&radiusKm=20');
  assert(nearbyRes.status === 200, 'Nearby search endpoint returned HTTP 200');
  assert(Array.isArray(nearbyRes.json), 'Nearby search returns an array of providers');

  console.log('\n====================================================');
  console.log('ALL GOOGLE MAPS INTEGRATION TESTS PASSED (100%)');
  console.log('====================================================');
}

runMapsTests().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
