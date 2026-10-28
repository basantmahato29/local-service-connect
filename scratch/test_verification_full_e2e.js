const http = require('http');

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
      if (Buffer.isBuffer(body)) {
        postData = body;
      } else if (typeof body === 'object') {
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

function buildMultipart(fields, fileField) {
  const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);
  const crlf = '\r\n';
  let parts = [];

  for (const [k, v] of Object.entries(fields)) {
    parts.push(Buffer.from(`--${boundary}${crlf}Content-Disposition: form-data; name="${k}"${crlf}${crlf}${v}${crlf}`));
  }

  if (fileField) {
    const header = `--${boundary}${crlf}Content-Disposition: form-data; name="${fileField.name}"; filename="${fileField.filename}"${crlf}Content-Type: ${fileField.contentType}${crlf}${crlf}`;
    parts.push(Buffer.from(header));
    parts.push(fileField.buffer);
    parts.push(Buffer.from(crlf));
  }

  parts.push(Buffer.from(`--${boundary}--${crlf}`));
  const buffer = Buffer.concat(parts);

  return {
    contentType: `multipart/form-data; boundary=${boundary}`,
    buffer
  };
}

function createFakeJpeg() {
  const buf = Buffer.alloc(100);
  buf[0] = 0xFF; buf[1] = 0xD8; buf[2] = 0xFF; buf[3] = 0xE0;
  buf[4] = 0x00; buf[5] = 0x10; buf[6] = 0x4A; buf[7] = 0x46; // JFIF
  return buf;
}

function createFakePdf() {
  return Buffer.from('%PDF-1.4\n%Fake PDF content for test\n%%EOF\n');
}

function createCorruptedFile() {
  return Buffer.from('NOT A REAL JPEG OR PDF DATA');
}

async function run() {
  console.log('====================================================');
  console.log('STARTING COMPREHENSIVE E2E VERIFICATION SUITE');
  console.log('====================================================\n');

  let passed = 0;
  let failed = 0;

  function assert(cond, msg) {
    if (cond) {
      console.log(`  [PASS] ${msg}`);
      passed++;
    } else {
      console.error(`  [FAIL] ${msg}`);
      failed++;
    }
  }

  // 1. Authenticate Admin
  console.log('1. Authenticating Admin...');
  const adminLogin = await request('POST', '/auth/login', {
    email: 'admin@localserviceconnect.com',
    password: 'Admin@12345'
  });
  assert(adminLogin.status === 200 && adminLogin.body.token, 'Admin authenticated successfully');
  const adminToken = adminLogin.body.token;

  // 2. Register Individual Service Professional
  console.log('\n2. Registering Individual Service Professional...');
  const ts = Date.now();
  const indEmail = `electrician_${ts}@test.com`;
  const indReg = await request('POST', '/auth/register', {
    name: 'Ramesh Sharma',
    email: indEmail,
    password: 'Password@123',
    phone: `98765${String(ts).slice(-5)}`,
    role: 'PROVIDER',
    businessName: 'Ramesh Electrical Services'
  });
  assert(indReg.status === 200 || indReg.status === 201, 'Individual provider registered successfully');
  const indToken = indReg.body.token;

  // Verify initial profile status is PENDING
  const indProfile = await request('GET', '/provider/profile', null, { 'Authorization': 'Bearer ' + indToken });
  assert(indProfile.status === 200 && indProfile.body.verificationStatus === 'PENDING', 'Initial provider verificationStatus is PENDING');

  // Verify unverified provider cannot appear in verified customer search
  const searchUnverified = await request('GET', '/providers/search?service=Electrician');
  const foundInSearch = Array.isArray(searchUnverified.body) && searchUnverified.body.some(p => p.id === indProfile.body.id);
  assert(!foundInSearch, 'Unverified provider does NOT appear in verified customer search');

  // Verify unverified provider cannot activate online availability (403 Forbidden)
  const unverifiedAvailCheck = await request('PUT', '/provider/availability', { available: true }, { 'Authorization': 'Bearer ' + indToken });
  assert(unverifiedAvailCheck.status === 403, 'Unverified provider blocked from going online (403 Forbidden: admin must verify first)');

  // 3. Fetch initial verification application
  console.log('\n3. Fetching Initial Verification Application...');
  const initApp = await request('GET', '/provider/verification/application', null, { 'Authorization': 'Bearer ' + indToken });
  assert(initApp.status === 200 && initApp.body.status === 'DRAFT', 'Initial verification application status is DRAFT');

  // 4. Save Individual Draft
  console.log('\n4. Saving Individual Draft (Step 1, 2, 3)...');
  const draftRes = await request('POST', '/provider/verification/draft', {
    providerType: 'INDIVIDUAL',
    legalName: 'Ramesh Kumar Sharma',
    dob: '1990-05-15',
    idType: 'PAN',
    idNumber: 'ABCPS1234F',
    serviceAddress: 'Flat 302, Green Glen Layout',
    city: 'Bengaluru',
    locality: 'Bellandur',
    pincode: '560103',
    privacyConsent: true,
    servicesOffered: 'Electrician, Home Wiring, Inverter Repair',
    serviceAreas: 'Bellandur, HSR Layout, Sarjapur'
  }, { 'Authorization': 'Bearer ' + indToken });
  assert(draftRes.status === 200 && draftRes.body.legalName === 'Ramesh Kumar Sharma', 'Draft saved with identity details');
  assert(draftRes.body.providerType === 'INDIVIDUAL', 'Provider category saved as INDIVIDUAL');

  // 5. Document Upload Security Validations
  console.log('\n5. Document Upload Security Validations...');
  // 5a. Corrupted file rejected by magic byte inspection
  const badMp = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'fake_pan.jpg',
    contentType: 'image/jpeg',
    buffer: createCorruptedFile()
  });
  const badUpload = await request('POST', '/provider/verification/documents', badMp.buffer, {
    'Authorization': 'Bearer ' + indToken,
    'Content-Type': badMp.contentType
  });
  assert(badUpload.status === 400, 'Corrupted / fake image file rejected by magic bytes inspection (HTTP 400)');

  // 5b. Valid JPEG Government ID
  const goodMp = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'pan_card.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  const goodUpload = await request('POST', '/provider/verification/documents', goodMp.buffer, {
    'Authorization': 'Bearer ' + indToken,
    'Content-Type': goodMp.contentType
  });
  assert(goodUpload.status === 200 && goodUpload.body.documentType === 'IDENTITY_PROOF', 'Valid PAN Card uploaded and security validated');
  const idDocId = goodUpload.body.id;

  // 5c. Access Control: Anonymous access to private document must be rejected
  const anonDocCheck = await request('GET', `/verification/documents/${idDocId}/file`);
  assert(anonDocCheck.status === 401 || anonDocCheck.status === 403, 'Anonymous access to private document is blocked (HTTP 401/403)');

  // 6. Submit Application
  console.log('\n6. Submitting Verification Application...');
  const submitRes = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + indToken });
  assert(submitRes.status === 200 && submitRes.body.status === 'SUBMITTED', 'Application transitioned to SUBMITTED');
  assert(submitRes.body.referenceNumber && submitRes.body.referenceNumber.startsWith('APP-'), `Generated reference number: ${submitRes.body.referenceNumber}`);
  const appId = submitRes.body.id;

  // Duplicate submission safety
  const dupSubmit = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + indToken });
  assert(dupSubmit.status === 200 && dupSubmit.body.referenceNumber === submitRes.body.referenceNumber, 'Duplicate submission safely returns single existing application reference without creating duplicate records');

  // Security: Unauthorized provider blocked from accessing admin verification endpoints
  const unauthAdmin = await request('GET', '/admin/verification/applications', null, { 'Authorization': 'Bearer ' + indToken });
  assert(unauthAdmin.status === 403, 'Unauthorized provider blocked from admin verification endpoints (HTTP 403)');

  // 7. Admin Inspection & Workflow
  console.log('\n7. Admin Application Inspection & Workflow...');
  const adminList = await request('GET', '/admin/verification/applications?status=SUBMITTED', null, { 'Authorization': 'Bearer ' + adminToken });
  const appList = Array.isArray(adminList.body) ? adminList.body : (adminList.body.content || []);
  assert(adminList.status === 200 && appList.some(a => a.id === appId), 'Admin can list submitted applications');

  // Admin views full details
  const appDetail = await request('GET', `/admin/verification/applications/${appId}`, null, { 'Authorization': 'Bearer ' + adminToken });
  assert(appDetail.status === 200 && appDetail.body.legalName === 'Ramesh Kumar Sharma', 'Admin retrieved application details');
  assert(appDetail.body.documents.length >= 1, 'Admin sees uploaded documents');

  // Admin inspects document via authenticated stream
  const docStream = await request('GET', `/verification/documents/${idDocId}/file`, null, { 'Authorization': 'Bearer ' + adminToken });
  assert(docStream.status === 200, 'Admin can securely inspect private document stream');

  // 8. Admin Action A: Start Review
  console.log('\n8. Admin Action: Start Review...');
  const startRev = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'START_REVIEW',
    notes: 'Officer reviewing ID details'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(startRev.status === 200 && startRev.body.status === 'UNDER_REVIEW', 'Status transitioned to UNDER_REVIEW');

  // 9. Admin Action B: Request More Information
  console.log('\n9. Admin Action: Request Information (ACTION_REQUIRED)...');
  const reqInfo = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'REQUEST_INFO',
    reason: 'ID photo is slightly blurred. Please confirm full name spelling.',
    notes: 'Requested clarification on legal name'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(reqInfo.status === 200 && reqInfo.body.status === 'ACTION_REQUIRED', 'Status transitioned to ACTION_REQUIRED');

  // Provider receives notification
  const provNotifs = await request('GET', '/notifications', null, { 'Authorization': 'Bearer ' + indToken });
  assert(provNotifs.status === 200 && provNotifs.body.some(n => n.title.includes('Action Required') || n.title.includes('Corrections Needed')), 'Provider received notification for ACTION_REQUIRED');

  // 10. Provider Corrects & Resubmits Application
  console.log('\n10. Provider Corrects & Resubmits Application...');
  await request('POST', '/provider/verification/draft', {
    legalName: 'Ramesh Kumar Sharma'
  }, { 'Authorization': 'Bearer ' + indToken });
  const resubmit = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + indToken });
  assert(resubmit.status === 200 && resubmit.body.status === 'SUBMITTED', 'Resubmission successful, status back to SUBMITTED');
  assert(resubmit.body.history.length >= 4, `Audit history preserved across resubmissions (${resubmit.body.history.length} events)`);

  // Admin starts review again
  await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'START_REVIEW'
  }, { 'Authorization': 'Bearer ' + adminToken });

  // 11. Checklist Enforcement on Approval
  console.log('\n11. Checklist Enforcement on Approval...');
  const prematureApproval = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'APPROVE',
    notes: 'Premature approval'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(prematureApproval.status === 400, 'Premature approval without checklist passes rejected with HTTP 400');

  // 12. Admin Completes Checklist & Approves Provider
  console.log('\n12. Admin Completes Checklist & Approves Provider...');
  const indChecklistRes = await request('PUT', `/admin/verification/applications/${appId}/checklist`, {
    identityDocReceived: true,
    identityDocReadable: true,
    nameAndDetailsReviewed: true,
    identityCheckAuthorized: true,
    contactVerified: true
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(indChecklistRes.status === 200 && indChecklistRes.body.allChecked === true, 'Admin completed individual checklist with all checks passed');

  const approvalRes = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'APPROVE',
    adminNotes: 'All checks passed for independent professional.'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(approvalRes.status === 200 && approvalRes.body.status === 'VERIFIED', 'Application approved and status set to VERIFIED');

  // Verify Provider entity status and booking privileges
  const updatedIndProfile = await request('GET', '/provider/profile', null, { 'Authorization': 'Bearer ' + indToken });
  assert(updatedIndProfile.body.verificationStatus === 'VERIFIED', 'Provider profile verificationStatus updated to VERIFIED');
  assert(updatedIndProfile.body.availability === false, 'Verification does NOT automatically change provider availability');

  // Provider configures service & location coverage
  await request('PUT', '/provider/profile', {
    businessName: 'Ramesh Electrical Services',
    serviceIds: [1],
    areas: [{ area: 'Bellandur', city: 'Bengaluru' }]
  }, { 'Authorization': 'Bearer ' + indToken });

  // Provider activates online dispatch
  const availToggle = await request('PUT', '/provider/availability', { available: true }, { 'Authorization': 'Bearer ' + indToken });
  assert(availToggle.status === 200 && availToggle.body.availability === true, 'Verified provider can now activate online dispatch');

  // Provider receives approval notification
  const approvalNotifs = await request('GET', '/notifications', null, { 'Authorization': 'Bearer ' + indToken });
  assert(approvalNotifs.status === 200 && approvalNotifs.body.some(n => n.title.includes('Approved') || n.title.includes('Verified')), 'Provider received approval notification');

  // 13. Shop / Business Flow
  console.log('\n13. Registering & Verifying Shop/Business Provider...');
  const bizEmail = `garage_${ts}@test.com`;
  const bizReg = await request('POST', '/auth/register', {
    name: 'Anil Verma',
    email: bizEmail,
    password: 'Password@123',
    phone: `98764${String(ts).slice(-5)}`,
    role: 'PROVIDER',
    businessName: 'Apex Motor Care Workshop'
  });
  const bizToken = bizReg.body.token;

  // Save Business Draft (requires exterior photo & location proof)
  const bizDraft = await request('POST', '/provider/verification/draft', {
    providerType: 'BUSINESS',
    legalName: 'Anil Kumar Verma',
    idType: 'DRIVING_LICENSE',
    idNumber: 'DL-0420110012345',
    serviceAddress: '12, 1st Cross, Indiranagar',
    privacyConsent: true,
    businessName: 'Apex Motor Care Workshop',
    businessType: 'Automobile Repair Garage',
    businessAddress: 'Plot 45, 100 Feet Road, Indiranagar',
    shopAddress: 'Plot 45, 100 Feet Road, Indiranagar',
    businessPhone: '080-25251122',
    locationProofType: 'RENT_AGREEMENT',
    servicesOffered: 'Car Servicing, Wheel Alignment, AC Repair',
    serviceAreas: 'Indiranagar, Domlur, Old Airport Road'
  }, { 'Authorization': 'Bearer ' + bizToken });
  assert(bizDraft.status === 200 && bizDraft.body.providerType === 'BUSINESS', 'Business draft saved with shop details');

  // Premature submit without shop documents fails
  const prematureBizSubmit = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + bizToken });
  assert(prematureBizSubmit.status === 400, 'Business submission rejected when shop exterior or location proof missing');

  // Upload Business Documents: ID, Shop Exterior, Rent Agreement
  const bIdMp = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'license.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  await request('POST', '/provider/verification/documents', bIdMp.buffer, { 'Authorization': 'Bearer ' + bizToken, 'Content-Type': bIdMp.contentType });

  const bShopMp = buildMultipart({ type: 'SHOP_EXTERIOR' }, {
    name: 'file',
    filename: 'shop_front.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  await request('POST', '/provider/verification/documents', bShopMp.buffer, { 'Authorization': 'Bearer ' + bizToken, 'Content-Type': bShopMp.contentType });

  const bRentMp = buildMultipart({ type: 'LOCATION_PROOF' }, {
    name: 'file',
    filename: 'lease_deed.pdf',
    contentType: 'application/pdf',
    buffer: createFakePdf()
  });
  await request('POST', '/provider/verification/documents', bRentMp.buffer, { 'Authorization': 'Bearer ' + bizToken, 'Content-Type': bRentMp.contentType });

  // Submit business application
  const bizSubmit = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + bizToken });
  assert(bizSubmit.status === 200 && bizSubmit.body.status === 'SUBMITTED', 'Business verification application submitted with all 3 required documents');
  const bizAppId = bizSubmit.body.id;

  // Admin completes 9-point business checklist
  const bizChecklistRes = await request('PUT', `/admin/verification/applications/${bizAppId}/checklist`, {
    identityDocReceived: true,
    identityDocReadable: true,
    nameAndDetailsReviewed: true,
    identityCheckAuthorized: true,
    businessInfoReviewed: true,
    shopPhotosReviewed: true,
    businessAddressReviewed: true,
    locationProofReviewed: true,
    contactVerified: true
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(bizChecklistRes.status === 200 && bizChecklistRes.body.allChecked === true, 'Admin completed business checklist with all 9 checks passed');

  // Admin approves business application with complete checklist
  const bizApproval = await request('POST', `/admin/verification/applications/${bizAppId}/action`, {
    action: 'APPROVE',
    adminNotes: 'Shop premises and lease deed verified on site.'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(bizApproval.status === 200 && bizApproval.body.status === 'VERIFIED', 'Business provider approved with 9-point checklist');

  // 14. Rejection Workflow
  console.log('\n14. Provider Rejection Workflow...');
  const rejEmail = `cleaner_${ts}@test.com`;
  const rejReg = await request('POST', '/auth/register', {
    name: 'Pooja Devi',
    email: rejEmail,
    password: 'Password@123',
    phone: `98763${String(ts).slice(-5)}`,
    role: 'PROVIDER',
    businessName: 'Sparkle Cleaning'
  });
  const rejToken = rejReg.body.token;

  await request('POST', '/provider/verification/draft', {
    providerType: 'INDIVIDUAL',
    legalName: 'Pooja Devi',
    idType: 'VOTER_ID',
    idNumber: 'ABC1234567',
    serviceAddress: 'Sector 4, HSR',
    privacyConsent: true
  }, { 'Authorization': 'Bearer ' + rejToken });

  const rIdMp = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'voter_id.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  await request('POST', '/provider/verification/documents', rIdMp.buffer, { 'Authorization': 'Bearer ' + rejToken, 'Content-Type': rIdMp.contentType });
  const rejSubmit = await request('POST', '/provider/verification/submit', null, { 'Authorization': 'Bearer ' + rejToken });
  const rejAppId = rejSubmit.body.id;

  const rejAction = await request('POST', `/admin/verification/applications/${rejAppId}/action`, {
    action: 'REJECT',
    reason: 'Document details do not match official voter identity registry.',
    notes: 'Failed background verification check'
  }, { 'Authorization': 'Bearer ' + adminToken });
  assert(rejAction.status === 200 && rejAction.body.status === 'REJECTED', 'Application successfully rejected with recorded reason');
  assert(rejAction.body.rejectionReason.includes('voter identity registry'), 'Rejection reason preserved in application record');

  // Summary
  console.log('\n====================================================');
  console.log(`E2E SUITE COMPLETED: ${passed} Passed, ${failed} Failed`);
  console.log('====================================================\n');
  if (failed > 0) process.exit(1);
}

run().catch(err => {
  console.error('Test execution error:', err);
  process.exit(1);
});
