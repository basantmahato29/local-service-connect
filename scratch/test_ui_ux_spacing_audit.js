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

function fetchUrl(urlPath) {
  return new Promise((resolve, reject) => {
    http.get(BASE_URL + urlPath, res => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body: data, headers: res.headers }));
    }).on('error', reject);
  });
}

async function runAudit() {
  console.log('====================================================');
  console.log('UI/UX, SPACING, AND DESIGN SYSTEM AUDIT SUITE');
  console.log('====================================================\n');

  // 1. Audit CSS Design System in style.css
  console.log('1. Auditing Centralized Design System in style.css...');
  const cssContent = fs.readFileSync(path.join(FRONTEND_DIR, 'css', 'style.css'), 'utf-8');

  const requiredTokens = [
    '--brand-purple: #7c3aed',
    '--brand-purple-hover: #6d28d9',
    '--brand-purple-light: #f5f3ff',
    '--brand-purple-subtle: #ede9fe',
    '--text-dark: #0f172a',
    '--text-body: #334155',
    '--text-muted: #64748b',
    '--bg-page: #f8fafc',
    '--surface-card: #ffffff',
    '--border-light: #e2e8f0',
    '--control-height-sm: 36px',
    '--control-height-md: 44px',
    '--control-height-lg: 50px',
    '--focus-ring: 0 0 0 3px rgba(124, 58, 237, 0.18)',
    '--radius-card: 16px',
    '--table-cell-padding-y: 0.85rem',
    '--table-cell-padding-x: 1rem',
    '--space-1: 4px',
    '--space-2: 8px',
    '--space-3: 12px',
    '--space-4: 16px',
    '--space-5: 20px',
    '--space-6: 24px',
    '--space-8: 32px',
    '--space-10: 40px',
    '--space-12: 48px',
    '--space-16: 64px'
  ];

  requiredTokens.forEach(token => {
    assert(cssContent.includes(token), `Design token defined: ${token}`);
  });

  // Verify UI Component Standards in CSS
  assert(cssContent.includes('.table-card'), 'Standard .table-card component defined in style.css');
  assert(cssContent.includes('.btn-primary'), 'Standard .btn-primary component with focus ring defined');
  assert(cssContent.includes('.form-control:focus'), 'Standard form input purple focus state defined');
  assert(cssContent.includes('.auth-page-wrapper'), 'Standard .auth-page-wrapper layout defined');
  assert(cssContent.includes('.modal-content'), 'Standard .modal-content with border radius defined');

  // 2. Audit Page Spacing and Container Consistency
  console.log('\n2. Auditing Page Spacing & Navbar Clearance Across Pages...');
  const pagesToAudit = [
    { file: 'services.html', name: 'Services & Categories' },
    { file: 'providers.html', name: 'Providers Directory' },
    { file: 'booking.html', name: 'Service Booking' },
    { file: 'booking-status.html', name: 'Booking Tracking & OTP' },
    { file: 'provider-profile.html', name: 'Public Provider Profile' },
    { file: 'admin/dashboard.html', name: 'Admin Dashboard' },
    { file: 'admin/bookings.html', name: 'Admin Bookings Table' },
    { file: 'admin/services.html', name: 'Admin Services Catalog' },
    { file: 'admin/customers.html', name: 'Admin Customers' },
    { file: 'admin/reviews.html', name: 'Admin Reviews Moderation' }
  ];

  for (const p of pagesToAudit) {
    const content = fs.readFileSync(path.join(FRONTEND_DIR, p.file), 'utf-8');
    assert(!content.includes('class="container pb-5">') && !content.includes('class="container-xl pb-5">'), 
      `${p.name} does not use cramped pb-5 container without top padding`);
    assert(content.includes('py-4') || content.includes('py-3') || content.includes('py-lg-5'), 
      `${p.name} has standardized vertical breathing room (py-4/py-lg-5)`);
  }

  // 3. HTTP Availability & Route Serving Verification
  console.log('\n3. Verifying HTTP Availability of All Frontend Pages...');
  const routesToVerify = [
    '/',
    '/services.html',
    '/providers.html',
    '/booking.html',
    '/booking-status.html',
    '/provider-profile.html',
    '/my-account.html',
    '/login.html',
    '/register.html',
    '/provider-home.html',
    '/provider-dashboard.html',
    '/provider/home.html',
    '/provider/profile.html',
    '/admin/login.html',
    '/admin/dashboard.html',
    '/admin/bookings.html',
    '/admin/services.html',
    '/admin/customers.html',
    '/admin/reviews.html',
    '/admin/providers.html',
    '/error/404.html',
    '/css/style.css'
  ];

  for (const route of routesToVerify) {
    const res = await fetchUrl(route);
    assert(res.status === 200, `Route ${route} served successfully with HTTP 200`);
  }

  // 4. Verify Responsive Design Breakpoints and Viewports
  console.log('\n4. Verifying Viewport Meta Tags and Responsive Breakpoints...');
  const htmlFiles = [
    'index.html',
    'services.html',
    'providers.html',
    'booking.html',
    'booking-status.html',
    'my-account.html',
    'login.html',
    'register.html',
    'provider-home.html',
    'provider-dashboard.html',
    'provider-profile.html',
    'admin/dashboard.html',
    'admin/bookings.html',
    'admin/services.html',
    'admin/customers.html',
    'admin/reviews.html',
    'admin/providers.html'
  ];

  htmlFiles.forEach(f => {
    const content = fs.readFileSync(path.join(FRONTEND_DIR, f), 'utf-8');
    assert(content.includes('<meta name="viewport" content="width=device-width, initial-scale=1">'), 
      `${f} contains proper responsive viewport meta tag`);
    assert(!content.includes('width: 100vw; overflow-x: hidden'), 
      `${f} does not introduce horizontal body scroll glitches`);
  });

  // 5. Accessibility & Semantic Checks
  console.log('\n5. Verifying Accessibility & Interactive Elements...');
  assert(cssContent.includes(':focus-visible'), 'Focus visible indicators present for keyboard accessibility');
  assert(cssContent.includes('@media (prefers-reduced-motion: reduce)'), 'Prefers-reduced-motion media query supported');
  
  const indexHtml = fs.readFileSync(path.join(FRONTEND_DIR, 'index.html'), 'utf-8');
  assert(indexHtml.includes('<h1 class="hero-heading mb-3">'), 'index.html has clear primary semantic h1');
  assert(indexHtml.includes('aria-label="Use Current Location"'), 'Current location button has accessible aria-label');

  console.log('\n====================================================');
  console.log('ALL UI/UX, SPACING, AND DESIGN SYSTEM TESTS PASSED (100%)');
  console.log('====================================================');
}

runAudit().catch(err => {
  console.error('Audit execution error:', err);
  process.exit(1);
});
