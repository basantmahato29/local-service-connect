const assert = require('assert');

async function verify() {
  console.log('--- 1. Testing /api/config/maps endpoint ---');
  const res = await fetch('http://localhost:8080/api/config/maps');
  assert.strictEqual(res.status, 200, 'Config endpoint must return HTTP 200');
  const config = await res.json();
  console.log('Maps Config:', config);
  assert(config.configured === true, 'Configured flag should be true');
  assert(typeof config.apiKey === 'string' && config.apiKey.length > 10, 'Valid API key string must be present');
  assert.strictEqual(config.defaultLat, 23.3441, 'Default latitude must be Ranchi');
  assert.strictEqual(config.defaultLng, 85.3096, 'Default longitude must be Ranchi');

  console.log('--- 2. Testing /index.html served by Spring Boot ---');
  const htmlRes = await fetch('http://localhost:8080/index.html');
  assert.strictEqual(htmlRes.status, 200, 'index.html must return HTTP 200');
  const html = await htmlRes.text();
  
  // Ensure NO Leaflet CSS or Leaflet JS
  assert(!html.includes('leaflet.css'), 'index.html must not contain leaflet.css');
  assert(!html.includes('leaflet.js'), 'index.html must not contain leaflet.js');
  assert(!html.includes('nominatim.openstreetmap.org'), 'index.html must not contain Nominatim URLs');
  assert(!html.includes('modalLeafletMap'), 'index.html must not contain modalLeafletMap');
  
  // Ensure Google Maps integration elements
  assert(html.includes('/js/maps.js'), 'index.html must import /js/maps.js');
  assert(html.includes('modalInteractiveMap'), 'index.html must contain modalInteractiveMap container');
  assert(html.includes('bookingMapPicker'), 'index.html must contain bookingMapPicker controller');
  assert(html.includes('btnModalUseGps'), 'index.html must contain btnModalUseGps');
  assert(html.includes('mapSearchInput'), 'index.html must contain mapSearchInput for Places autocomplete');
  assert(html.includes('pac-container'), 'index.html must contain pac-container modal z-index styling');
  assert(html.includes('syncBookingLocation'), 'index.html must contain syncBookingLocation state synchronizer');
  console.log('All frontend checks passed: 0 Leaflet / 0 Nominatim, Google Maps integration confirmed!');

  console.log('--- 3. Testing /services.html served by Spring Boot ---');
  const sHtmlRes = await fetch('http://localhost:8080/services.html');
  const sHtml = await sHtmlRes.text();
  assert(!sHtml.includes('leaflet.css'), 'services.html must not contain leaflet.css');
  assert(!sHtml.includes('leaflet.js'), 'services.html must not contain leaflet.js');
  assert(sHtml.includes('/js/maps.js'), 'services.html must import /js/maps.js');
  console.log('services.html clean: 0 Leaflet, maps.js imported!');

  console.log('--- 4. Testing /provider-dashboard.html served by Spring Boot ---');
  const pDashRes = await fetch('http://localhost:8080/provider-dashboard.html');
  const pDashHtml = await pDashRes.text();
  assert(!pDashHtml.includes('leaflet.css'), 'provider-dashboard.html must not contain leaflet.css');
  assert(!pDashHtml.includes('leaflet.js'), 'provider-dashboard.html must not contain leaflet.js');
  console.log('provider-dashboard.html clean: 0 Leaflet!');

  console.log('\n=========================================');
  console.log('SUCCESS: All Static & Configuration tests passed cleanly!');
  console.log('=========================================');
}

verify().catch(err => {
  console.error('Verification failed:', err);
  process.exit(1);
});
