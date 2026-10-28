const http = require('http');

const API_BASE = 'http://localhost:8080/api';

function request(method, path, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(API_BASE + path);
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
  console.log('TESTING FAVORITE PROVIDERS BACKEND REST APIS');
  console.log('====================================================');

  const ts = Date.now();

  // 1. Register Customer A
  console.log('\n1. Registering Customer A...');
  const custAEmail = `cust_a_${ts}@test.com`;
  const regA = await request('POST', '/auth/register', {
    name: 'Customer Alice',
    email: custAEmail,
    password: 'Password@123',
    role: 'CUSTOMER',
    phone: '9876543210'
  });
  assert((regA.status === 201 || regA.status === 200) && regA.body.token, 'Customer A registered with token');
  const tokenA = regA.body.token;

  // 2. Register Customer B
  console.log('\n2. Registering Customer B...');
  const custBEmail = `cust_b_${ts}@test.com`;
  const regB = await request('POST', '/auth/register', {
    name: 'Customer Bob',
    email: custBEmail,
    password: 'Password@123',
    role: 'CUSTOMER',
    phone: '9876543211'
  });
  assert((regB.status === 201 || regB.status === 200) && regB.body.token, 'Customer B registered with token');
  const tokenB = regB.body.token;

  // 3. Get Verified Providers
  console.log('\n3. Fetching public verified providers...');
  const provRes = await request('GET', '/providers');
  assert(provRes.status === 200 && Array.isArray(provRes.body) && provRes.body.length > 0, 'Found verified providers');
  const targetProvider = provRes.body[0];
  const targetProviderId = targetProvider.id;
  console.log(`  Target provider: ID=${targetProviderId}, Name="${targetProvider.businessName}"`);

  // 4. Test Unauthenticated Access
  console.log('\n4. Testing Unauthenticated Access...');
  const unauthAdd = await request('POST', `/favorites/${targetProviderId}`);
  assert(unauthAdd.status === 401, 'Anonymous request to add favorite is rejected (HTTP 401)');

  const unauthList = await request('GET', '/favorites');
  assert(unauthList.status === 401, 'Anonymous request to list favorites is rejected (HTTP 401)');

  // 5. Customer A Adds Target Provider to Favorites
  console.log('\n5. Customer A toggles favorite on provider...');
  const toggleRes1 = await request('POST', `/favorites/${targetProviderId}`, null, tokenA);
  assert(toggleRes1.status === 200, 'Toggle response is 200 OK');
  assert(toggleRes1.body.isFavorite === true, 'Provider is now favorited');
  assert(toggleRes1.body.providerId === targetProviderId, 'Returned provider ID matches');

  // 6. Check Single Status
  console.log('\n6. Checking single provider favorite status...');
  const statusResA = await request('GET', `/favorites/${targetProviderId}/status`, null, tokenA);
  assert(statusResA.status === 200, 'Status endpoint returned 200');
  assert(statusResA.body.isFavorite === true, 'Status confirms provider is favorited');
  assert(statusResA.body.notifyOnAvailable === false, 'Default notifyOnAvailable is false');

  // 7. Check Batch IDs for Customer A
  console.log('\n7. Checking batch favorite IDs...');
  const batchResA = await request('GET', '/favorites/ids', null, tokenA);
  assert(batchResA.status === 200, 'Batch IDs returned 200');
  assert(Array.isArray(batchResA.body.favoriteProviderIds), 'Returned favoriteProviderIds array');
  assert(batchResA.body.favoriteProviderIds.includes(targetProviderId), 'Batch array contains targetProviderId');

  // 8. Retrieve Full Favorites List for Customer A
  console.log('\n8. Retrieving full favorites list for Customer A...');
  const listResA = await request('GET', '/favorites', null, tokenA);
  assert(listResA.status === 200, 'Favorites list returned 200');
  assert(Array.isArray(listResA.body) && listResA.body.length === 1, 'Customer A has exactly 1 favorite');
  assert(listResA.body[0].providerId === targetProviderId, 'Favorite entry references correct provider');
  assert(listResA.body[0].provider.businessName === targetProvider.businessName, 'Provider DTO is fully populated');
  // Check sensitive fields are NOT exposed
  assert(!listResA.body[0].provider.providerEmail, 'Provider private email is not exposed in public DTO');
  assert(!listResA.body[0].provider.providerPhone, 'Provider private phone is not exposed in public DTO');

  // 9. Customer B Isolation (Cross-customer check)
  console.log('\n9. Checking Customer B isolation...');
  const listResB = await request('GET', '/favorites', null, tokenB);
  assert(listResB.status === 200 && Array.isArray(listResB.body) && listResB.body.length === 0, 'Customer B favorites list is empty (isolated)');

  const statusResB = await request('GET', `/favorites/${targetProviderId}/status`, null, tokenB);
  assert(statusResB.status === 200 && statusResB.body.isFavorite === false, 'Customer B status shows isFavorite: false');

  // 10. Invalid and Non-Existent Provider Tests
  console.log('\n10. Testing Invalid and Non-Existent Providers...');
  const notFoundRes = await request('POST', '/favorites/999999', null, tokenA);
  assert(notFoundRes.status === 404, 'Non-existent provider returns 404 Not Found');

  const invalidIdRes = await request('POST', '/favorites/0', null, tokenA);
  assert(invalidIdRes.status === 400, 'Invalid provider ID 0 returns 400 Bad Request');

  // 11. Update Notification Preference
  console.log('\n11. Updating availability notification preference...');
  const notifyUpdate = await request('PUT', `/favorites/${targetProviderId}/notify`, { notifyOnAvailable: true }, tokenA);
  assert(notifyUpdate.status === 200, 'Notification update returned 200');
  assert(notifyUpdate.body.notifyOnAvailable === true, 'Preference successfully updated to true');

  const statusAfterNotify = await request('GET', `/favorites/${targetProviderId}/status`, null, tokenA);
  assert(statusAfterNotify.body.notifyOnAvailable === true, 'Persisted preference is true');

  // 12. Remove Favorite
  console.log('\n12. Removing favorite via DELETE endpoint...');
  const deleteRes = await request('DELETE', `/favorites/${targetProviderId}`, null, tokenA);
  assert(deleteRes.status === 200, 'DELETE returned 200');
  assert(deleteRes.body.isFavorite === false, 'DELETE confirms isFavorite: false');

  const listAfterDelete = await request('GET', '/favorites', null, tokenA);
  assert(listAfterDelete.body.length === 0, 'Customer A favorites list is now empty');

  // 13. Idempotent DELETE
  console.log('\n13. Idempotent DELETE on non-favorited provider...');
  const repeatDelete = await request('DELETE', `/favorites/${targetProviderId}`, null, tokenA);
  assert(repeatDelete.status === 200 && repeatDelete.body.isFavorite === false, 'Repeated DELETE is safe and idempotent');

  console.log('\n====================================================');
  console.log('ALL BACKEND FAVORITE TESTS PASSED SUCCESSFULLY! (13/13)');
  console.log('====================================================');
}

run().catch(err => {
  console.error('\nTEST SUITE FAILED:', err);
  process.exit(1);
});
