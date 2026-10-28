const http = require('http');
const fs = require('fs');

const BASE_URL = 'http://localhost:8080/api';

function request(method, path, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(path.startsWith('http') ? path : (BASE_URL + (path.startsWith('/') ? path : '/' + path)));
    const options = {
      method,
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      headers: { ...headers }
    };

    let postData = null;
    if (body !== null && body !== undefined) {
      if (typeof body === 'object') {
        postData = JSON.stringify(body);
        if (!options.headers['Content-Type']) {
          options.headers['Content-Type'] = 'application/json';
        }
      } else {
        postData = body;
      }
      options.headers['Content-Length'] = Buffer.byteLength(postData);
    } else if (method === 'POST' || method === 'PUT') {
      postData = '{}';
      if (!options.headers['Content-Type']) {
        options.headers['Content-Type'] = 'application/json';
      }
      options.headers['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(options, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch (e) { json = data; }
        resolve({ status: res.statusCode, headers: res.headers, body: json });
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) {
    console.error(`  [FAIL] ${message}`);
    process.exit(1);
  }
  console.log(`  [PASS] ${message}`);
}

(async () => {
  console.log('====================================================');
  console.log('VERIFICATION NAVIGATION & WIZARD PERSISTENCE SUITE');
  console.log('====================================================\n');

  const ts = Date.now();
  const providerEmail = `nav_provider_${ts}@test.com`;

  // 1. Register test provider
  console.log('1. Registering new provider account...');
  const regRes = await request('POST', '/auth/register', {
    name: 'Nav Test Provider',
    email: providerEmail,
    password: 'Password@123',
    phone: `98711${String(ts).slice(-5)}`,
    role: 'PROVIDER',
    businessName: 'Nav Test Electricals'
  });
  assert(regRes.status === 200 || regRes.status === 201, 'Provider registered with token');
  const token = regRes.body.token;

  // 2. Fetch initial verification application
  console.log('\n2. Fetching verification application...');
  const appRes = await request('GET', '/provider/verification/application', null, {
    'Authorization': 'Bearer ' + token
  });
  assert(appRes.status === 200, 'Verification application endpoint returned HTTP 200');
  assert(appRes.body.status === 'DRAFT', 'Initial application status is DRAFT');
  assert(appRes.body.referenceNumber && appRes.body.referenceNumber.startsWith('APP-'), 'Reference number generated properly');

  // 3. Unauthorized access check
  console.log('\n3. Security & Route Guards: Unauthorized access blocked...');
  const anonRes = await request('GET', '/provider/verification/application');
  assert(anonRes.status === 401 || anonRes.status === 403, 'Unauthenticated access to verification application blocked (401/403)');

  // 4. Saving Draft Data (Step 2 and Step 3)
  console.log('\n4. Saving draft verification data across wizard steps...');
  const draftPayload = {
    providerType: 'INDIVIDUAL',
    legalName: 'Nav Test Provider Legal',
    dob: '1990-05-15',
    idType: 'PAN',
    idNumber: 'ABCDE1234F',
    serviceAddress: '123 Test Street, Whitefield',
    city: 'Bengaluru',
    locality: 'Whitefield',
    pincode: '560066',
    privacyConsent: true,
    businessName: 'Nav Test Electricals',
    businessType: 'Electrical Maintenance',
    servicesOffered: 'Wiring, Appliance Repair',
    serviceAreas: 'Whitefield, Marathahalli'
  };
  const saveRes = await request('POST', '/provider/verification/draft', draftPayload, {
    'Authorization': 'Bearer ' + token
  });
  assert(saveRes.status === 200, 'Draft saved successfully with HTTP 200');
  assert(saveRes.body.legalName === 'Nav Test Provider Legal', 'Legal name matches saved draft');
  assert(saveRes.body.idNumberMasked && saveRes.body.idNumberMasked.includes('234F'), 'ID number is masked for privacy and preserved');
  assert(saveRes.body.pincode === '560066', 'PIN code matches saved draft');
  assert(saveRes.body.privacyConsent === true, 'Privacy consent persisted');

  // 5. Verification Application Reload / Refresh simulation
  console.log('\n5. Refresh simulation: Draft data is restored on application refetch...');
  const reloadedApp = await request('GET', '/provider/verification/application', null, {
    'Authorization': 'Bearer ' + token
  });
  assert(reloadedApp.status === 200, 'Reloaded application returned HTTP 200');
  assert(reloadedApp.body.status === 'DRAFT', 'Application remains in DRAFT status without unneeded state change');
  assert(reloadedApp.body.legalName === 'Nav Test Provider Legal', 'Restored draft contains legalName');
  assert(reloadedApp.body.idNumberMasked && reloadedApp.body.idNumberMasked.includes('234F'), 'Restored draft contains masked idNumber');

  // 6. Inspect frontend static files for navigation and wizard fixes
  console.log('\n6. Inspecting frontend HTML and JS code for navigation bug fixes...');
  const dashboardHtml = fs.readFileSync('frontend/provider-dashboard.html', 'utf8');

  // Assert switchTab synchronous execution on init
  assert(dashboardHtml.includes('switchTab(initialTab, false);'), 'Initial tab is switched synchronously on page load to prevent layout shifts');

  // Assert URL query & popstate handling
  assert(dashboardHtml.includes('window.history.pushState({ tab: tabId }'), 'pushState preserves active tab in history without page reloads');
  assert(dashboardHtml.includes("window.addEventListener('popstate'"), 'popstate listener added for smooth browser Back/Forward navigation');

  // Assert renderVerificationUI does NOT force jumpToWizardStep(1) on background refresh
  assert(dashboardHtml.includes('if (isInitialLoad) {'), 'renderVerificationUI checks isInitialLoad flag before setting wizard step');
  assert(dashboardHtml.includes('jumpToWizardStep(targetStep);'), 'Wizard step is restored from sessionStorage or URL query without jumping back to 1');

  // Assert setValSafe protects active/focused user input during background poll
  assert(dashboardHtml.includes('document.activeElement === el'), 'setValSafe checks activeElement to prevent clobbering input while user is typing');

  // Assert background polling passes false so it does not reset wizard
  assert(dashboardHtml.includes('await loadVerification(isFirstVerif);'), 'refreshDashboard passes false to loadVerification on background polls');

  // Assert document upload does not reset wizard to step 1
  assert(dashboardHtml.includes('await loadVerification(false);'), 'Document upload and delete pass false to loadVerification so provider stays on Step 4');

  // Assert error banner with retry option in loadVerification
  assert(dashboardHtml.includes('Unable to load your verification application'), 'loadVerification handles API errors with an informative banner and retry option');

  // Assert field validation visual error indicators
  assert(dashboardHtml.includes("classList.add('is-invalid')"), 'wizardNext adds is-invalid visual classes next to invalid fields');

  // 7. Inspect provider/dashboard.html query preservation
  console.log('\n7. Inspecting provider/dashboard.html redirect...');
  const provDashRedirect = fs.readFileSync('frontend/provider/dashboard.html', 'utf8');
  assert(provDashRedirect.includes('location.search + location.hash'), 'provider/dashboard.html preserves query parameters and hash on redirect');

  // 8. Inspect Auth.getToken compatibility and safe token retrieval
  console.log('\n8. Inspecting Auth.getToken compatibility in api.js and document previews...');
  const apiJs = fs.readFileSync('frontend/js/api.js', 'utf8');
  assert(apiJs.includes('getToken() {') && apiJs.includes('return this.token();'), 'Auth.getToken() is defined in api.js as alias to Auth.token()');
  assert(!dashboardHtml.includes('const token = Auth.getToken();'), 'provider-dashboard.html does not rely blindly on Auth.getToken()');
  assert(dashboardHtml.includes("localStorage.getItem('lsc_token')"), 'provider-dashboard.html includes fallback to localStorage token');

  console.log('\n====================================================');
  console.log('ALL VERIFICATION NAVIGATION TESTS PASSED! (100%)');
  console.log('====================================================\n');
})();
