const fs = require('fs');
const path = require('path');
const http = require('http');

const BASE_URL = 'http://localhost:8080';
const FRONTEND_DIR = path.join(__dirname, '..', 'frontend');

function assert(condition, message) {
  if (!condition) {
    console.error(`  [FAIL] ${message}`);
    process.exit(1);
  }
  console.log(`  [PASS] ${message}`);
}

function request(method, path, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path.startsWith('http') ? path : (BASE_URL + path));
    const headers = { 'Accept': path.startsWith('/api') ? 'application/json' : 'text/html,application/xhtml+xml,*/*;q=0.8' };
    let postData = null;
    if (body) {
      postData = typeof body === 'string' ? body : JSON.stringify(body);
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = Buffer.byteLength(postData);
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const req = http.request(url, { method, headers }, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch {}
        resolve({ status: res.statusCode, body: json || data, raw: data, headers: res.headers });
      });
    });
    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function runAudit() {
  console.log('====================================================');
  console.log('LOCAL SERVICE CONNECT: COMPREHENSIVE PLATFORM AUDIT');
  console.log('====================================================\n');

  const ts = Date.now();

  // ----------------------------------------------------
  // SECTION 1: ROUTE INVENTORY & HTTP 200 AVAILABILITY
  // ----------------------------------------------------
  console.log('--- SECTION 1: ROUTE INVENTORY & HTTP AVAILABILITY ---');
  const allRoutes = [
    { path: '/', type: 'Public Visitor' },
    { path: '/services.html', type: 'Public / Customer' },
    { path: '/providers.html', type: 'Public / Customer' },
    { path: '/provider-profile.html', type: 'Public Profile' },
    { path: '/booking.html', type: 'Customer Booking' },
    { path: '/booking-status.html', type: 'Customer Status & OTP' },
    { path: '/my-account.html', type: 'Customer Portal' },
    { path: '/login.html', type: 'Auth Login' },
    { path: '/register.html', type: 'Auth Register' },
    { path: '/provider-home.html', type: 'Provider Homepage' },
    { path: '/provider-dashboard.html', type: 'Provider Console' },
    { path: '/provider/home.html', type: 'Provider Home' },
    { path: '/provider/profile.html', type: 'Provider Profile' },
    { path: '/admin/login.html', type: 'Admin Login' },
    { path: '/admin/dashboard.html', type: 'Admin Dashboard' },
    { path: '/admin/bookings.html', type: 'Admin Bookings' },
    { path: '/admin/services.html', type: 'Admin Services' },
    { path: '/admin/customers.html', type: 'Admin Customers' },
    { path: '/admin/reviews.html', type: 'Admin Reviews' },
    { path: '/admin/providers.html', type: 'Admin Verification Queue' },
    { path: '/error/404.html', type: 'Error 404' }
  ];

  for (const r of allRoutes) {
    const res = await request('GET', r.path);
    assert(res.status === 200, `[${r.type}] ${r.path} returns HTTP 200`);
  }

  // ----------------------------------------------------
  // SECTION 2: AUTHENTICATION & ROLE-BASED ACCESS CONTROL
  // ----------------------------------------------------
  console.log('\n--- SECTION 2: AUTHENTICATION & ROLE-BASED ACCESS CONTROL ---');
  const custEmail = `audit_cust_${ts}@test.com`;
  const provEmail = `audit_prov_${ts}@test.com`;
  const password = 'Password@123';

  // 1. Customer registration & login
  const custReg = await request('POST', '/api/auth/register', {
    name: 'Audit Customer', email: custEmail, password, role: 'CUSTOMER', phone: `98711${String(ts).slice(-5)}`
  });
  assert(custReg.status === 200 || custReg.status === 201, 'Customer registered successfully');
  const custToken = custReg.body.token;
  assert(custToken, 'Customer registration issued JWT token');

  const custLogin = await request('POST', '/api/auth/login', { email: custEmail, password });
  assert(custLogin.status === 200, 'Customer logged in successfully');
  assert(custLogin.body.role === 'CUSTOMER', 'Customer role returned as CUSTOMER');

  // 2. Provider registration & login
  const provReg = await request('POST', '/api/auth/register', {
    name: 'Audit Provider', email: provEmail, password, role: 'PROVIDER', phone: `98722${String(ts).slice(-5)}`, businessName: 'Audit Auto Clinic'
  });
  assert(provReg.status === 200 || provReg.status === 201, 'Provider registered successfully');
  const provToken = provReg.body.token;
  assert(provToken, 'Provider registration issued JWT token');

  const provLogin = await request('POST', '/api/auth/login', { email: provEmail, password });
  assert(provLogin.status === 200, 'Provider logged in successfully');
  assert(provLogin.body.role === 'PROVIDER', 'Provider role returned as PROVIDER');

  // 3. Admin login
  const adminLogin = await request('POST', '/api/auth/login', { email: 'admin@localserviceconnect.com', password: 'Admin@12345' });
  assert(adminLogin.status === 200, 'Admin logged in with default platform credentials');
  const adminToken = adminLogin.body.token;
  assert(adminLogin.body.role === 'ADMIN', 'Admin role verified as ADMIN');

  // 4. Invalid credentials rejection
  const badLogin = await request('POST', '/api/auth/login', { email: custEmail, password: 'WrongPassword999!' });
  assert(badLogin.status === 400 || badLogin.status === 401, 'Bad credentials safely rejected');

  // 5. Duplicate registration rejection
  const dupReg = await request('POST', '/api/auth/register', {
    name: 'Dup User', email: custEmail, password, role: 'CUSTOMER', phone: `98733${String(ts).slice(-5)}`
  });
  assert(dupReg.status === 400 || dupReg.status === 409, 'Duplicate email registration rejected');

  // ----------------------------------------------------
  // SECTION 3: SECURITY AUDIT & ACCESS CONTROL
  // ----------------------------------------------------
  console.log('\n--- SECTION 3: SECURITY AUDIT & ACCESS CONTROL ---');

  // 1. Unauthenticated access blocked
  const anonAdmin = await request('GET', '/api/admin/customers');
  assert(anonAdmin.status === 401 || anonAdmin.status === 403, 'Anonymous access to /api/admin/customers blocked (HTTP 401/403)');

  // 2. Customer trying to access Admin endpoints blocked
  const custAdmin = await request('GET', '/api/admin/customers', null, custToken);
  assert(custAdmin.status === 403, 'Customer token blocked from /api/admin/customers (HTTP 403 Forbidden)');

  // 3. Customer trying to access Provider endpoints blocked
  const custProv = await request('GET', '/api/provider/dashboard', null, custToken);
  assert(custProv.status === 403, 'Customer token blocked from /api/provider/dashboard (HTTP 403 Forbidden)');

  // 4. Provider trying to access Admin endpoints blocked
  const provAdmin = await request('GET', '/api/admin/bookings', null, provToken);
  assert(provAdmin.status === 403, 'Provider token blocked from /api/admin/bookings (HTTP 403 Forbidden)');

  // 5. SQL Injection attack resistance test
  const sqliTest = await request('POST', '/api/auth/login', {
    email: "' OR 1=1 --",
    password: "' OR '1'='1"
  });
  assert(sqliTest.status === 400 || sqliTest.status === 401, 'SQL Injection in auth credentials safely rejected');

  // 6. XSS payload sanitization check on public profile endpoints
  const xssSearch = await request('GET', '/api/services/search?query=' + encodeURIComponent('<script>alert("xss")</script>'));
  assert(xssSearch.status === 200, 'Search endpoint handled potential XSS payload safely');
  assert(!xssSearch.raw.includes('<script>alert('), 'Raw script tags not reflected unescaped');

  // ----------------------------------------------------
  // SECTION 4: CORE CUSTOMER WORKFLOWS
  // ----------------------------------------------------
  console.log('\n--- SECTION 4: CUSTOMER FUNCTIONALITY & WORKFLOWS ---');

  // 1. Service Catalog retrieval
  const svcsRes = await request('GET', '/api/services');
  assert(svcsRes.status === 200 && Array.isArray(svcsRes.body) && svcsRes.body.length > 0, 'Active services retrieved from catalog');
  const targetService = svcsRes.body[0];

  // 2. Search nearby providers
  const nearbyRes = await request('GET', `/api/providers/nearby?serviceId=${targetService.id}&latitude=23.3441&longitude=85.3096`);
  assert(nearbyRes.status === 200 && Array.isArray(nearbyRes.body), 'Nearby providers query returned HTTP 200');

  // 3. Customer profile address update
  const profRes = await request('PUT', '/api/customer/profile', {
    address: '42 Main Road', area: 'Lalpur', city: 'Ranchi', pincode: '834001', latitude: 23.3685, longitude: 85.3340
  }, custToken);
  assert(profRes.status === 200, 'Customer address updated with coordinates');
  assert(profRes.body.area === 'Lalpur', 'Customer profile verified with saved area');

  // 4. Notifications endpoint
  const notifRes = await request('GET', '/api/notifications', null, custToken);
  assert(notifRes.status === 200 && Array.isArray(notifRes.body), 'Customer notifications retrieved');

  // ----------------------------------------------------
  // SECTION 5: PERFORMANCE & CONCURRENCY BENCHMARK
  // ----------------------------------------------------
  console.log('\n--- SECTION 5: PERFORMANCE & CONCURRENCY BENCHMARK ---');

  // Measure single page latency
  const t0 = Date.now();
  await request('GET', '/');
  const homeLatency = Date.now() - t0;
  console.log(`  Initial Homepage Latency: ${homeLatency} ms`);
  assert(homeLatency < 500, `Homepage load latency is acceptable (${homeLatency} ms < 500 ms)`);

  // Concurrent request stress test (25 concurrent requests across various endpoints)
  const concurrentTasks = [];
  const startConc = Date.now();
  for (let i = 0; i < 25; i++) {
    const ep = i % 3 === 0 ? '/api/config/maps' : (i % 3 === 1 ? '/api/services' : '/api/providers/nearby?latitude=23.3441&longitude=85.3096');
    concurrentTasks.push(request('GET', ep));
  }
  const results = await Promise.all(concurrentTasks);
  const totalConcTime = Date.now() - startConc;
  const avgLatency = Math.round(totalConcTime / results.length);
  const allPassed = results.every(r => r.status === 200);

  console.log(`  Completed 25 concurrent requests in ${totalConcTime} ms (Avg: ${avgLatency} ms/request)`);
  assert(allPassed, 'All 25 concurrent requests returned HTTP 200 with zero errors');
  assert(avgLatency < 100, `Concurrent throughput latency is high performance (${avgLatency} ms/request < 100 ms)`);

  // ----------------------------------------------------
  // SECTION 6: RESPONSIVE & ACCESSIBILITY AUDIT
  // ----------------------------------------------------
  console.log('\n--- SECTION 6: RESPONSIVE & ACCESSIBILITY AUDIT ---');
  const css = fs.readFileSync(path.join(FRONTEND_DIR, 'css', 'style.css'), 'utf-8');
  assert(css.includes(':focus-visible'), 'Focus-visible outlines implemented for keyboard accessibility');
  assert(css.includes('@media (prefers-reduced-motion: reduce)'), 'Prefers-reduced-motion supported');

  const index = fs.readFileSync(path.join(FRONTEND_DIR, 'index.html'), 'utf-8');
  assert(index.includes('<meta name="viewport" content="width=device-width, initial-scale=1">'), 'Responsive viewport tag present');
  assert(index.includes('<h1 class="hero-heading mb-3">'), 'Semantic H1 heading present');

  console.log('\n====================================================');
  console.log('ALL COMPREHENSIVE PLATFORM AUDIT TESTS PASSED (100%)');
  console.log('====================================================');
}

runAudit().catch(err => {
  console.error('Audit execution error:', err);
  process.exit(1);
});
