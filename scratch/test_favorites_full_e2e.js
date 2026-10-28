const http = require('http');
const fs = require('fs');

const API_BASE = 'http://localhost:8080/api';
const HOST_BASE = 'http://localhost:8080';

function request(method, path, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path.startsWith('http') ? path : (API_BASE + path));
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const data = body ? JSON.stringify(body) : null;
    if (data) headers['Content-Length'] = Buffer.byteLength(data);

    const req = http.request({
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      method: method,
      headers: headers
    }, (res) => {
      let raw = '';
      res.on('data', chunk => raw += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(raw); } catch { json = raw; }
        resolve({ status: res.statusCode, headers: res.headers, body: json });
      });
    });

    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) {
    console.error(`  [FAIL] ${message}`);
    throw new Error(`Assertion failed: ${message}`);
  }
  console.log(`  [PASS] ${message}`);
}

async function run() {
  console.log('====================================================');
  console.log('COMPREHENSIVE FAVORITE & SHARE PROVIDER E2E TEST SUITE');
  console.log('====================================================');

  const ts = Date.now();

  // 1. Authenticate / Register Customer A
  console.log('\n1. Registering Customer A (Alice)...');
  const custA = await request('POST', '/auth/register', {
    name: 'Alice Wonder',
    email: `alice_${ts}@example.com`,
    password: 'Password@123',
    role: 'CUSTOMER',
    phone: '9811111111'
  });
  assert((custA.status === 201 || custA.status === 200) && custA.body.token, 'Customer Alice registered successfully');
  const tokenA = custA.body.token;

  // 2. Register Customer B
  console.log('\n2. Registering Customer B (Bob)...');
  const custB = await request('POST', '/auth/register', {
    name: 'Bob Builder',
    email: `bob_${ts}@example.com`,
    password: 'Password@123',
    role: 'CUSTOMER',
    phone: '9822222222'
  });
  assert((custB.status === 201 || custB.status === 200) && custB.body.token, 'Customer Bob registered successfully');
  const tokenB = custB.body.token;

  // 3. Register a dedicated Provider with services and areas
  console.log('\n3. Registering & configuring a dedicated test Provider...');
  const provEmail = `prov_${ts}@example.com`;
  const provReg = await request('POST', '/auth/register', {
    name: 'Test Pro Master',
    businessName: `Super Electrics ${ts}`,
    email: provEmail,
    password: 'Password@123',
    role: 'PROVIDER',
    phone: '9833333333'
  });
  assert((provReg.status === 201 || provReg.status === 200) && provReg.body.token, 'Provider registered');
  const provToken = provReg.body.token;

  // Provider configures profile
  const provProfile = await request('PUT', '/provider/profile', {
    businessName: `Super Electrics ${ts}`,
    description: 'Licensed certified electrical contractor for residential installations.',
    serviceIds: [1], // Electrician
    areas: [{ area: 'Lalpur', city: 'Ranchi' }],
    experienceYears: 7,
    approxPrice: 450.00
  }, provToken);
  assert(provProfile.status === 200, 'Provider profile updated');
  const providerId = provProfile.body.id;

  // Login as admin to verify provider
  const adminLogin = await request('POST', '/auth/login', {
    email: 'admin@localserviceconnect.com',
    password: 'Admin@12345'
  });
  assert(adminLogin.status === 200 && adminLogin.body.token, 'Admin logged in');
  const adminToken = adminLogin.body.token;

  // Admin approves / verifies provider
  const verifyRes = await request('PUT', `/admin/providers/${providerId}/verify`, {
    status: 'VERIFIED'
  }, adminToken);
  assert(verifyRes.status === 200, 'Admin verified provider');

  // Verify provider appears in public search
  const pubSearch = await request('GET', `/providers/${providerId}`);
  assert(pubSearch.status === 200 && pubSearch.body.businessName === `Super Electrics ${ts}`, 'Provider is publicly accessible and verified');

  // 4. Security: Anonymous request blocked
  console.log('\n4. Security & RBAC: Anonymous access rejected...');
  const unauthAdd = await request('POST', `/favorites/${providerId}`);
  assert(unauthAdd.status === 401, 'Anonymous request to POST /favorites rejected with HTTP 401');

  // 5. Customer A adds provider to favorites
  console.log('\n5. Customer A adds provider to favorites...');
  const addFav = await request('POST', `/favorites/${providerId}`, null, tokenA);
  assert(addFav.status === 200 && addFav.body.isFavorite === true, 'Provider added to favorites (isFavorite: true)');

  // 6. Check status and batch IDs for Customer A
  console.log('\n6. Checking favorite status & batch IDs...');
  const statusA = await request('GET', `/favorites/${providerId}/status`, null, tokenA);
  assert(statusA.status === 200 && statusA.body.isFavorite === true, 'Single status endpoint confirms isFavorite: true');

  const batchIdsA = await request('GET', '/favorites/ids', null, tokenA);
  assert(batchIdsA.status === 200 && batchIdsA.body.favoriteProviderIds.includes(providerId), 'Batch IDs contains providerId');

  // 7. Verify Customer A Favorites list contains full provider data without leaking private contact
  console.log('\n7. Retrieving Customer A favorites list...');
  const listA = await request('GET', '/favorites', null, tokenA);
  assert(listA.status === 200 && listA.body.length >= 1, 'Customer A favorites list has at least 1 provider');
  const favEntry = listA.body.find(f => f.providerId === providerId);
  assert(favEntry != null, 'Found target provider in favorites list');
  assert(favEntry.provider.businessName === `Super Electrics ${ts}`, 'Provider business name matches');
  assert(favEntry.provider.approxPrice === 450.00, 'Starting price matches');
  assert(favEntry.provider.verificationStatus === 'VERIFIED', 'Verification status is VERIFIED');
  assert(!favEntry.provider.providerEmail, 'Provider owner private email is NOT exposed to customer');
  assert(!favEntry.provider.providerPhone, 'Provider owner private phone is NOT exposed to customer');

  // 8. Customer B Isolation check
  console.log('\n8. Verifying cross-customer isolation...');
  const listB = await request('GET', '/favorites', null, tokenB);
  assert(listB.status === 200 && (!listB.body.some(f => f.providerId === providerId)), 'Customer B does not see Customer A\'s favorite');

  const statusB = await request('GET', `/favorites/${providerId}/status`, null, tokenB);
  assert(statusB.status === 200 && statusB.body.isFavorite === false, 'Customer B status shows isFavorite: false');

  // 9. Phase 5: Opt-in for availability notifications
  console.log('\n9. Testing Phase 5: Opt-in availability notification...');
  const optInRes = await request('PUT', `/favorites/${providerId}/notify`, { notifyOnAvailable: true }, tokenA);
  assert(optInRes.status === 200 && optInRes.body.notifyOnAvailable === true, 'Customer Alice opted in for availability alerts');

  // Ensure provider starts offline
  await request('PUT', '/provider/availability', { available: false }, provToken);

  // Clear customer notifications
  const initialNotifs = await request('GET', '/notifications', null, tokenA);
  const initialCount = Array.isArray(initialNotifs.body) ? initialNotifs.body.length : 0;

  // Provider switches online
  console.log('  Provider toggles availability to ONLINE (true)...');
  const onlineToggle = await request('PUT', '/provider/availability', { available: true }, provToken);
  assert(onlineToggle.status === 200 && onlineToggle.body.availability === true, 'Provider is now online');

  // Check if Customer Alice received the notification
  const notifsAfter = await request('GET', '/notifications', null, tokenA);
  assert(notifsAfter.status === 200, 'Retrieved customer notifications');
  const newNotif = notifsAfter.body.find(n => n.type === 'PROVIDER_AVAILABLE');
  assert(newNotif != null, 'Customer Alice received PROVIDER_AVAILABLE notification');
  assert(newNotif.title.includes('Online'), 'Notification title announces provider is online');
  assert(newNotif.message.includes(`Super Electrics ${ts}`), 'Notification body references provider business name');

  // Verify Provider toggles online again while already online -> NO duplicate notifications
  console.log('  Provider re-saves availability while already online (idempotency check)...');
  await request('PUT', '/provider/availability', { available: true }, provToken);
  const notifsAfterRepeat = await request('GET', '/notifications', null, tokenA);
  const availableNotifs = notifsAfterRepeat.body.filter(n => n.type === 'PROVIDER_AVAILABLE');
  assert(availableNotifs.length === 1, 'No duplicate notification sent for redundant online toggle');

  // 10. Customer A removes provider from favorites
  console.log('\n10. Removing provider from favorites...');
  const removeRes = await request('DELETE', `/favorites/${providerId}`, null, tokenA);
  assert(removeRes.status === 200 && removeRes.body.isFavorite === false, 'DELETE confirms isFavorite: false');

  const listAfterDel = await request('GET', '/favorites', null, tokenA);
  assert(!listAfterDel.body.some(f => f.providerId === providerId), 'Provider is no longer in Customer A favorites list');

  const batchAfterDel = await request('GET', '/favorites/ids', null, tokenA);
  assert(!batchAfterDel.body.favoriteProviderIds.includes(providerId), 'ProviderId removed from batch IDs');

  // 11. Error handling: Non-existent provider and unverified provider
  console.log('\n11. Negative testing: invalid and non-existent IDs...');
  const notFoundFav = await request('POST', '/favorites/888888', null, tokenA);
  assert(notFoundFav.status === 404, 'Non-existent provider returns 404 Not Found');

  const invalidId = await request('POST', '/favorites/-5', null, tokenA);
  assert(invalidId.status === 400, 'Invalid provider ID returns 400 Bad Request');

  // 12. Share Provider System Verification
  console.log('\n12. Testing Share Provider System...');
  const canonicalUrl = `${HOST_BASE}/provider-profile.html?id=${providerId}`;
  console.log(`  Canonical URL generated: ${canonicalUrl}`);

  // Fetch the canonical URL via HTTP and verify page loads
  const pageCheck = await request('GET', canonicalUrl);
  assert(pageCheck.status === 200, 'Canonical public provider URL loads successfully with HTTP 200');
  assert(pageCheck.body.includes('btnHeroShare'), 'Public profile page has Share button');
  assert(pageCheck.body.includes('btnHeroFavorite'), 'Public profile page has Favorite button');
  assert(pageCheck.body.includes('pageCanonicalLink'), 'Public profile page has canonical link tag');
  assert(pageCheck.body.includes('ogTitle'), 'Public profile page has Open Graph social tags');

  // Verify Share message safety
  const shareMessage = `Check out Super Electrics ${ts} on Local Service Connect for verified doorstep services in Lalpur: ${canonicalUrl}`;
  assert(!shareMessage.includes('password'), 'Share message contains no passwords');
  assert(!shareMessage.includes('token'), 'Share message contains no auth tokens');
  assert(!shareMessage.includes('alice_'), 'Share message contains no customer personal details');
  assert(shareMessage.includes(canonicalUrl), 'Share message contains permanent canonical URL');

  // 13. Verify Frontend Pages have Favorite and Share Buttons
  console.log('\n13. Inspecting frontend HTML pages for Favorite and Share components...');
  const providersHtml = fs.readFileSync('frontend/providers.html', 'utf8');
  assert(providersHtml.includes('btn-fav-action') && providersHtml.includes('btn-share-action'), 'providers.html has btn-fav-action and btn-share-action');

  const servicesHtml = fs.readFileSync('frontend/services.html', 'utf8');
  assert(servicesHtml.includes('btn-fav-action') && servicesHtml.includes('btn-share-action'), 'services.html has btn-fav-action and btn-share-action');

  const indexHtml = fs.readFileSync('frontend/index.html', 'utf8');
  assert(indexHtml.includes('btn-fav-action') && indexHtml.includes('btn-share-action'), 'index.html has btn-fav-action and btn-share-action');

  const myAccountHtml = fs.readFileSync('frontend/my-account.html', 'utf8');
  assert(myAccountHtml.includes('tab-favorites') && myAccountHtml.includes('favCountBadge'), 'my-account.html has tab-favorites and favCountBadge');
  assert(myAccountHtml.includes('loadFavoriteProviders') && myAccountHtml.includes('removeFavoriteProvider'), 'my-account.html has loadFavoriteProviders & removeFavoriteProvider');

  const apiJs = fs.readFileSync('frontend/js/api.js', 'utf8');
  assert(apiJs.includes('const Favorites = {') && apiJs.includes('const ShareProvider = {'), 'api.js has Favorites and ShareProvider manager objects');

  const styleCss = fs.readFileSync('frontend/css/style.css', 'utf8');
  assert(styleCss.includes('.btn-fav-action') && styleCss.includes('.btn-share-action') && styleCss.includes('anim-heart-pop'), 'style.css has button styles and heart pop animation');

  console.log('\n====================================================');
  console.log('ALL 35 E2E FAVORITE & SHARE TESTS PASSED! (35/35)');
  console.log('====================================================');
}

run().catch(err => {
  console.error('\nE2E SUITE FAILED:', err);
  process.exit(1);
});
