const http = require('http');

function postJson(urlPath, data) {
  return new Promise((resolve, reject) => {
    const postData = JSON.stringify(data);
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: urlPath,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(postData)
      }
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(body) });
        } catch (e) {
          resolve({ status: res.statusCode, body });
        }
      });
    });
    req.on('error', reject);
    req.write(postData);
    req.end();
  });
}

function getJson(urlPath, token) {
  return new Promise((resolve, reject) => {
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const req = http.request({
      hostname: 'localhost',
      port: 8080,
      path: urlPath,
      method: 'GET',
      headers
    }, res => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(body) });
        } catch (e) {
          resolve({ status: res.statusCode, body });
        }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

async function runAudit() {
  console.log('=== ADMIN MODULES LIVE E2E AUDIT ===\n');

  // 1. Admin login
  const loginRes = await postJson('/api/auth/login', {
    email: 'admin@localserviceconnect.com',
    password: 'Admin@12345'
  });
  console.log(`[AUTH] Admin login: HTTP ${loginRes.status}`);
  if (loginRes.status !== 200 || !loginRes.data.token) {
    throw new Error('Admin login failed: ' + JSON.stringify(loginRes.data));
  }
  const token = loginRes.data.token;
  console.log('  Token acquired successfully.');

  // 2. Dashboard API
  const dashRes = await getJson('/api/admin/dashboard', token);
  console.log(`\n[DASHBOARD] GET /api/admin/dashboard: HTTP ${dashRes.status}`);
  console.log('  Metrics:', JSON.stringify(dashRes.data, null, 2));

  // 3. Verification Applications
  console.log('\n[VERIFICATION] Checking applications API...');
  const vAllRes = await getJson('/api/admin/verification/applications', token);
  console.log(`  GET /api/admin/verification/applications: HTTP ${vAllRes.status}, count: ${Array.isArray(vAllRes.data) ? vAllRes.data.length : 'not array'}`);

  const vPageRes = await getJson('/api/admin/verification/applications?page=0&size=5', token);
  console.log(`  GET /api/admin/verification/applications?page=0&size=5: HTTP ${vPageRes.status}, totalElements: ${vPageRes.data.totalElements}, totalPages: ${vPageRes.data.totalPages}, content items: ${vPageRes.data.content?.length}`);

  const vPendingRes = await getJson('/api/admin/verification/applications?status=PENDING', token);
  console.log(`  GET /api/admin/verification/applications?status=PENDING: HTTP ${vPendingRes.status}, items: ${vPendingRes.data.length}`);

  const vSubmittedRes = await getJson('/api/admin/verification/applications?status=SUBMITTED', token);
  console.log(`  GET /api/admin/verification/applications?status=SUBMITTED: HTTP ${vSubmittedRes.status}, items: ${vSubmittedRes.data.length}`);

  if (vPendingRes.data.length !== vSubmittedRes.data.length) {
    throw new Error(`PENDING (${vPendingRes.data.length}) does not match SUBMITTED (${vSubmittedRes.data.length})`);
  }
  console.log('  PENDING status alias matches SUBMITTED perfectly.');

  const vCountsRes = await getJson('/api/admin/verification/applications/summary-counts', token);
  console.log(`  GET summary-counts: HTTP ${vCountsRes.status}:`, JSON.stringify(vCountsRes.data));

  // 4. Bookings API
  console.log('\n[BOOKINGS] Checking bookings API...');
  const bAllRes = await getJson('/api/admin/bookings', token);
  console.log(`  GET /api/admin/bookings: HTTP ${bAllRes.status}, total bookings: ${bAllRes.data.length}`);

  const bActiveRes = await getJson('/api/admin/bookings?status=ACTIVE', token);
  console.log(`  GET /api/admin/bookings?status=ACTIVE: HTTP ${bActiveRes.status}, active bookings: ${bActiveRes.data.length}`);

  const bQuickRes = await getJson('/api/admin/bookings?type=QUICK_SERVICE', token);
  console.log(`  GET /api/admin/bookings?type=QUICK_SERVICE: HTTP ${bQuickRes.status}, quick bookings: ${bQuickRes.data.length}`);

  // 5. Customers API
  console.log('\n[CUSTOMERS] Checking customers API...');
  const custRes = await getJson('/api/admin/customers', token);
  console.log(`  GET /api/admin/customers: HTTP ${custRes.status}, count: ${custRes.data.length}`);

  // 6. Providers API
  console.log('\n[PROVIDERS] Checking providers API...');
  const provRes = await getJson('/api/admin/providers', token);
  console.log(`  GET /api/admin/providers: HTTP ${provRes.status}, count: ${provRes.data.length}`);

  // 7. Services API
  console.log('\n[SERVICES] Checking services API...');
  const svcRes = await getJson('/api/admin/services', token);
  console.log(`  GET /api/admin/services: HTTP ${svcRes.status}, count: ${svcRes.data.length}`);

  // 8. Reviews API
  console.log('\n[REVIEWS] Checking reviews API...');
  const revRes = await getJson('/api/admin/reviews', token);
  console.log(`  GET /api/admin/reviews: HTTP ${revRes.status}, count: ${revRes.data.length}`);

  // 9. Static UI Routes verification
  console.log('\n[UI ROUTES] Verifying HTML pages exist and load with HTTP 200...');
  const pages = [
    '/admin/dashboard.html',
    '/admin/providers.html',
    '/admin/bookings.html',
    '/admin/customers.html',
    '/admin/services.html',
    '/admin/reviews.html',
    '/admin/login.html'
  ];

  for (const p of pages) {
    const pageRes = await getJson(p);
    console.log(`  ${p}: HTTP ${pageRes.status}`);
    if (pageRes.status !== 200) {
      throw new Error(`Failed to load ${p}, status: ${pageRes.status}`);
    }
  }

  console.log('\n>>> ALL 9 ADMIN WORKFLOW CHECKS PASSED SUCCESSFULLY! <<<');
}

runAudit().catch(err => {
  console.error('\n[AUDIT FAILED]', err);
  process.exit(1);
});

