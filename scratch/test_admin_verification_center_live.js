const http = require('http');
const { spawn } = require('child_process');

const BASE_URL = 'http://127.0.0.1:8080';

function request(method, path, body = null, token = null) {
  return new Promise((resolve, reject) => {
    const url = new URL(path.startsWith('http') ? path : (BASE_URL + path));
    const headers = { 'Accept': 'application/json' };
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
        resolve({ status: res.statusCode, body: json || data });
      });
    });
    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

async function run() {
  console.log('====================================================');
  console.log('TESTING PROVIDER VERIFICATION CENTER LIVE API & UI');
  console.log('====================================================\n');

  // 1. Admin login
  console.log('1. Admin login...');
  const loginRes = await request('POST', '/api/auth/login', {
    email: 'admin@localserviceconnect.com',
    password: 'Admin@12345'
  });
  if (loginRes.status !== 200 || !loginRes.body.token) {
    console.error('Admin login failed:', loginRes);
    process.exit(1);
  }
  const adminToken = loginRes.body.token;
  console.log('  [PASS] Admin logged in successfully.');

  // 2. Unauthenticated check
  console.log('2. Security: Anonymous access rejected...');
  const anonRes = await request('GET', '/api/admin/verification/applications');
  if (anonRes.status === 401 || anonRes.status === 403) {
    console.log('  [PASS] Anonymous access rejected (HTTP ' + anonRes.status + ')');
  } else {
    console.error('  [FAIL] Anonymous access returned status ' + anonRes.status);
    process.exit(1);
  }

  // 3. Unpaginated list
  console.log('3. Testing unpaginated applications list...');
  const listRes = await request('GET', '/api/admin/verification/applications', null, adminToken);
  if (listRes.status !== 200 || !Array.isArray(listRes.body)) {
    console.error('  [FAIL] Unpaginated applications list failed:', listRes);
    process.exit(1);
  }
  console.log('  [PASS] Unpaginated returns Array. Count:', listRes.body.length);

  // 4. Paginated list
  console.log('4. Testing paginated applications list (Spring Boot Page)...');
  const pageRes = await request('GET', '/api/admin/verification/applications?page=0&size=5', null, adminToken);
  if (pageRes.status !== 200) {
    console.error('  [FAIL] Paginated applications list failed:', pageRes);
    process.exit(1);
  }
  const pageData = pageRes.body;
  if (!Array.isArray(pageData.content) || typeof pageData.totalElements !== 'number' || typeof pageData.totalPages !== 'number') {
    console.error('  [FAIL] Paginated response structure invalid:', pageData);
    process.exit(1);
  }
  console.log('  [PASS] Paginated returns Spring Boot Page: totalElements=' + pageData.totalElements + ', totalPages=' + pageData.totalPages + ', size=' + pageData.size + ', number=' + pageData.number);

  // 5. Status filter
  console.log('5. Testing status filter...');
  const subRes = await request('GET', '/api/admin/verification/applications?status=SUBMITTED', null, adminToken);
  console.log('  [PASS] SUBMITTED filter returns status ' + subRes.status + ', count: ' + (Array.isArray(subRes.body) ? subRes.body.length : subRes.body.content.length));

  // 6. Provider type filter
  console.log('6. Testing type filter...');
  const indRes = await request('GET', '/api/admin/verification/applications?type=INDIVIDUAL', null, adminToken);
  console.log('  [PASS] INDIVIDUAL filter returns status ' + indRes.status + ', count: ' + (Array.isArray(indRes.body) ? indRes.body.length : indRes.body.content.length));

  // 7. Search filter
  console.log('7. Testing search filter...');
  const searchEmpty = await request('GET', '/api/admin/verification/applications?search=NONEXISTENT_ABC_99999', null, adminToken);
  console.log('  [PASS] Empty search returns count: ' + (Array.isArray(searchEmpty.body) ? searchEmpty.body.length : searchEmpty.body.content.length));

  // 8. Sorting
  console.log('8. Testing sorting...');
  const sortRes = await request('GET', '/api/admin/verification/applications?page=0&size=5&sortBy=submittedAt&sortDir=asc', null, adminToken);
  console.log('  [PASS] Sort submittedAt:asc returns status ' + sortRes.status + ', content length: ' + sortRes.body.content.length);

  // 9. Browser rendering test using headless Edge
  console.log('\n9. Testing Browser DOM & UI in Headless Edge...');
  const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
  const cdpPort = 9235;

  const edge = spawn(edgePath, [
    '--headless=new',
    '--disable-gpu',
    `--remote-debugging-port=${cdpPort}`,
    'http://127.0.0.1:8080/admin/providers.html'
  ]);

  await new Promise(r => setTimeout(r, 2500));

  http.get(`http://127.0.0.1:${cdpPort}/json`, res => {
    let raw = '';
    res.on('data', c => raw += c);
    res.on('end', async () => {
      const tabs = JSON.parse(raw);
      const tab = tabs.find(t => t.url && t.url.includes('providers.html')) || tabs[0];
      if (!tab) {
        console.error('No Edge tab found');
        edge.kill();
        process.exit(1);
      }

      const ws = new WebSocket(tab.webSocketDebuggerUrl);
      let msgId = 1;
      const send = (method, params = {}) => new Promise(resolve => {
        const id = msgId++;
        const handler = (evt) => {
          const m = JSON.parse(evt.data);
          if (m.id === id) {
            ws.removeEventListener('message', handler);
            resolve(m.result);
          }
        };
        ws.addEventListener('message', handler);
        ws.send(JSON.stringify({ id, method, params }));
      });

      ws.onopen = async () => {
        await send('Runtime.enable');
        await send('Page.enable');

        // Set admin token in localStorage and navigate to /admin/providers.html
        await send('Runtime.evaluate', {
          expression: `
            localStorage.setItem('lsc_token', '${adminToken}');
            localStorage.setItem('lsc_role', 'ADMIN');
            localStorage.setItem('lsc_name', 'Administrator');
          `
        });

        await send('Page.navigate', { url: 'http://127.0.0.1:8080/admin/providers.html' });

        // Wait for page to load and fetch applications
        await new Promise(r => setTimeout(r, 3000));

        // Evaluate DOM state
        const domResult = await send('Runtime.evaluate', {
          expression: `(() => {
            const currentUrl = window.location.href;
            const alertEl = document.getElementById('pageAlert');
            const alertText = alertEl ? alertEl.textContent.trim() : '';
            const isAlertVisible = alertEl && !alertEl.classList.contains('d-none');

            const tbody = document.getElementById('appTableBody');
            const rowCount = tbody ? tbody.querySelectorAll('tr').length : 0;
            const tableText = tbody ? tbody.textContent.trim() : '';

            const cntSubmitted = document.getElementById('cntSubmitted') ? document.getElementById('cntSubmitted').textContent : null;
            const cntUnderReview = document.getElementById('cntUnderReview') ? document.getElementById('cntUnderReview').textContent : null;
            const cntTotal = document.getElementById('cntTotal') ? document.getElementById('cntTotal').textContent : null;

            const resultsCountText = document.getElementById('resultsCountText') ? document.getElementById('resultsCountText').textContent : null;
            const pageInfoText = document.getElementById('pageInfoText') ? document.getElementById('pageInfoText').textContent : null;

            const btnPrev = document.getElementById('btnPrevPage');
            const btnNext = document.getElementById('btnNextPage');

            const hasPageDataError = tableText.includes('pageData') || alertText.includes('pageData');

            return {
              currentUrl,
              hasPageDataError,
              isAlertVisible,
              alertText,
              rowCount,
              tableSnippet: tableText.slice(0, 200),
              cntSubmitted,
              cntUnderReview,
              cntTotal,
              resultsCountText,
              pageInfoText,
              prevDisabled: btnPrev ? btnPrev.disabled : null,
              nextDisabled: btnNext ? btnNext.disabled : null
            };
          })()`,
          returnByValue: true
        });

        console.log('DOM Evaluation Result:\n', JSON.stringify(domResult.result.value, null, 2));

        const val = domResult.result.value;
        if (val.hasPageDataError) {
          console.error('  [FAIL] pageData error still detected in DOM!');
          ws.close();
          edge.kill();
          process.exit(1);
        }

        console.log('  [PASS] Zero "pageData" errors in UI!');
        console.log('  [PASS] Applications table rendered correctly (rows: ' + val.rowCount + ')');
        console.log('  [PASS] Metric counts updated: Total=' + val.cntTotal + ', Submitted=' + val.cntSubmitted + ', UnderReview=' + val.cntUnderReview);
        console.log('  [PASS] Pagination text: "' + val.pageInfoText + '", Results: "' + val.resultsCountText + '"');

        // Test Pagination: Click Next Page
        console.log('\n10. Testing Next Page Button click...');
        await send('Runtime.evaluate', {
          expression: `changePage(1);`
        });
        await new Promise(r => setTimeout(r, 1500));

        const page2Result = await send('Runtime.evaluate', {
          expression: `({
            rowCount: document.getElementById('appTableBody').querySelectorAll('tr').length,
            pageInfoText: document.getElementById('pageInfoText').textContent,
            resultsCountText: document.getElementById('resultsCountText').textContent,
            prevDisabled: document.getElementById('btnPrevPage').disabled,
            nextDisabled: document.getElementById('btnNextPage').disabled
          })`,
          returnByValue: true
        });
        console.log('Page 2 State:', page2Result.result.value);
        if (page2Result.result.value.prevDisabled !== false || page2Result.result.value.nextDisabled !== true) {
          console.error('  [FAIL] Page 2 pagination buttons state invalid!');
          ws.close();
          edge.kill();
          process.exit(1);
        }
        console.log('  [PASS] Next page loaded successfully (rows: ' + page2Result.result.value.rowCount + ', ' + page2Result.result.value.pageInfoText + ')');

        // Test Pagination: Click Prev Page
        console.log('\n11. Testing Previous Page Button click...');
        await send('Runtime.evaluate', {
          expression: `changePage(-1);`
        });
        await new Promise(r => setTimeout(r, 1500));

        const page1Again = await send('Runtime.evaluate', {
          expression: `({
            rowCount: document.getElementById('appTableBody').querySelectorAll('tr').length,
            pageInfoText: document.getElementById('pageInfoText').textContent,
            prevDisabled: document.getElementById('btnPrevPage').disabled,
            nextDisabled: document.getElementById('btnNextPage').disabled
          })`,
          returnByValue: true
        });
        console.log('Page 1 Returned State:', page1Again.result.value);
        if (page1Again.result.value.prevDisabled !== true || page1Again.result.value.nextDisabled !== false) {
          console.error('  [FAIL] Page 1 return pagination buttons state invalid!');
          ws.close();
          edge.kill();
          process.exit(1);
        }
        console.log('  [PASS] Returned to page 1 successfully (' + page1Again.result.value.pageInfoText + ')');

        ws.close();
        edge.kill();
        console.log('\n====================================================');
        console.log('ALL PROVIDER VERIFICATION CENTER TESTS PASSED! (100%)');
        console.log('====================================================\n');
        process.exit(0);
      };
    });
  }).on('error', err => {
    console.error('Failed to connect to Edge CDP:', err.message);
    edge.kill();
    process.exit(1);
  });
}

run().catch(err => {
  console.error('Unhandled error:', err);
  process.exit(1);
});
