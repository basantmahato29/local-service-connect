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
    if (body) {
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

// Minimal valid JPEG header (FF D8 FF E0 00 10 4A 46 49 46 00 01 ...)
function createFakeJpeg() {
  const buf = Buffer.alloc(100);
  buf[0] = 0xFF; buf[1] = 0xD8; buf[2] = 0xFF; buf[3] = 0xE0;
  buf[4] = 0x00; buf[5] = 0x10; buf[6] = 0x4A; buf[7] = 0x46; // JFIF
  return buf;
}

// Minimal valid PDF header (%PDF-1.4 ...)
function createFakePdf() {
  return Buffer.from('%PDF-1.4\n%Fake PDF content for test\n%%EOF\n');
}

async function run() {
  console.log('--- Starting Provider Verification System Backend Test ---');

  // 1. Login as Admin
  const adminLogin = await request('POST', '/auth/login', {
    email: 'admin@localserviceconnect.com',
    password: 'Admin@12345'
  });
  if (adminLogin.status !== 200 || !adminLogin.body.token) {
    throw new Error('Admin login failed: ' + JSON.stringify(adminLogin.body));
  }
  const adminToken = adminLogin.body.token;
  console.log('✓ Admin login successful');

  // 2. Register/Login as a new provider
  const provEmail = 'testprovider_' + Date.now() + '@example.com';
  const regRes = await request('POST', '/auth/register', {
    name: 'Ramesh Sharma',
    email: provEmail,
    phone: '9876543210',
    password: 'Password@123',
    role: 'PROVIDER',
    businessName: 'Sharma Electrical Works'
  });
  if (regRes.status !== 200 && regRes.status !== 201) {
    throw new Error('Provider register failed: ' + JSON.stringify(regRes.body));
  }
  const provToken = regRes.body.token;
  console.log('✓ New Provider registered: ' + provEmail);

  // 3. Provider fetches initial verification application
  const appRes = await request('GET', '/provider/verification/application', null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (appRes.status !== 200) throw new Error('Fetch application failed: ' + JSON.stringify(appRes.body));
  console.log('✓ Initial Application fetched. Status: ' + appRes.body.status + ', Ref: ' + appRes.body.referenceNumber);
  if (appRes.body.status !== 'DRAFT') throw new Error('Expected status DRAFT');

  // 4. Provider saves draft
  const draftRes = await request('POST', '/provider/verification/draft', {
    providerType: 'INDIVIDUAL',
    legalName: 'Ramesh Kumar Sharma',
    idType: 'PAN',
    idNumber: 'ABCPS1234F',
    privacyConsent: true,
    serviceAddress: '123 Main Road, Indiranagar',
    city: 'Bengaluru',
    locality: 'Indiranagar',
    pincode: '560038'
  }, {
    'Authorization': 'Bearer ' + provToken
  });
  if (draftRes.status !== 200) throw new Error('Save draft failed: ' + JSON.stringify(draftRes.body));
  console.log('✓ Saved draft successfully. Legal Name: ' + draftRes.body.legalName + ', ID masked: ' + draftRes.body.idNumberMasked);

  // 5. Test File Validation: Reject text file pretending to be image
  const fakeBadFile = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'test.jpg',
    contentType: 'image/jpeg',
    buffer: Buffer.from('This is clearly plain text, not a real JPEG image!')
  });
  const badUploadRes = await request('POST', '/provider/verification/documents', fakeBadFile.buffer, {
    'Authorization': 'Bearer ' + provToken,
    'Content-Type': fakeBadFile.contentType
  });
  if (badUploadRes.status !== 400) {
    throw new Error('Expected 400 for fake/invalid magic bytes but got: ' + badUploadRes.status);
  }
  console.log('✓ File magic bytes validation successfully rejected invalid file content (400 Bad Request)');

  // 6. Test File Validation: Reject PDF for shop photo
  const fakePdfPhoto = buildMultipart({ type: 'SHOP_EXTERIOR' }, {
    name: 'file',
    filename: 'exterior.pdf',
    contentType: 'application/pdf',
    buffer: createFakePdf()
  });
  const badPhotoRes = await request('POST', '/provider/verification/documents', fakePdfPhoto.buffer, {
    'Authorization': 'Bearer ' + provToken,
    'Content-Type': fakePdfPhoto.contentType
  });
  if (badPhotoRes.status !== 400) {
    throw new Error('Expected 400 for PDF uploaded as shop photo but got: ' + badPhotoRes.status);
  }
  console.log('✓ Photo format validation successfully rejected PDF for shop exterior (400 Bad Request)');

  // 7. Upload genuine valid JPEG for IDENTITY_PROOF
  const validJpeg = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'pan_card.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  const goodUploadRes = await request('POST', '/provider/verification/documents', validJpeg.buffer, {
    'Authorization': 'Bearer ' + provToken,
    'Content-Type': validJpeg.contentType
  });
  if (goodUploadRes.status !== 200 || !goodUploadRes.body.id) {
    throw new Error('Valid upload failed: ' + JSON.stringify(goodUploadRes.body));
  }
  const uploadedDocId = goodUploadRes.body.id;
  console.log('✓ Valid identity document uploaded. Doc ID: ' + uploadedDocId + ', Download URL: ' + goodUploadRes.body.downloadUrl);

  // 8. Submit verification application
  const submitRes = await request('POST', '/provider/verification/submit', {
    providerType: 'INDIVIDUAL',
    legalName: 'Ramesh Kumar Sharma',
    idType: 'PAN',
    idNumber: 'ABCPS1234F',
    privacyConsent: true,
    serviceAddress: '123 Main Road, Indiranagar',
    city: 'Bengaluru',
    locality: 'Indiranagar',
    pincode: '560038'
  }, {
    'Authorization': 'Bearer ' + provToken
  });
  if (submitRes.status !== 200 || submitRes.body.status !== 'SUBMITTED') {
    throw new Error('Application submission failed: ' + JSON.stringify(submitRes.body));
  }
  const appId = submitRes.body.id;
  console.log('✓ Verification application submitted. ID: ' + appId + ', Status: ' + submitRes.body.status);

  // 9. Verify Security: Provider cannot access Admin endpoint
  const unauthorizedAdminCheck = await request('GET', '/admin/verification/applications', null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (unauthorizedAdminCheck.status !== 403) {
    throw new Error('Expected 403 Forbidden for provider accessing admin endpoints, got: ' + unauthorizedAdminCheck.status);
  }
  console.log('✓ Security: Provider forbidden from accessing admin endpoints (403 Forbidden)');

  // 10. Admin views applications
  const adminAppsRes = await request('GET', '/admin/verification/applications?status=SUBMITTED', null, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (adminAppsRes.status !== 200 || !Array.isArray(adminAppsRes.body)) {
    throw new Error('Admin applications list failed: ' + JSON.stringify(adminAppsRes.body));
  }
  const foundApp = adminAppsRes.body.find(a => a.id === appId);
  if (!foundApp) throw new Error('Submitted app not found in admin list');
  console.log('✓ Admin successfully found submitted application in Pending list');

  // 11. Admin attempts to APPROVE before checklist complete -> should fail 400
  const prematureApprove = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'APPROVE',
    adminNotes: 'Premature approval test'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (prematureApprove.status !== 400) {
    throw new Error('Expected 400 when approving without checklist complete, got: ' + prematureApprove.status);
  }
  console.log('✓ Admin approval correctly rejected when checklist items are incomplete (400 Bad Request)');

  // 12. Admin Starts Review
  const startReviewRes = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'START_REVIEW'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (startReviewRes.status !== 200 || startReviewRes.body.status !== 'UNDER_REVIEW') {
    throw new Error('Start review failed: ' + JSON.stringify(startReviewRes.body));
  }
  console.log('✓ Admin started review. Application status moved to: ' + startReviewRes.body.status);

  // 13. Admin requests corrections (ACTION_REQUIRED)
  const reqInfoRes = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'REQUEST_INFO',
    reason: 'Please upload a clearer copy of your PAN card. The name is partially blurred.',
    fields: 'IDENTITY_PROOF'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (reqInfoRes.status !== 200 || reqInfoRes.body.status !== 'ACTION_REQUIRED') {
    throw new Error('Request info failed: ' + JSON.stringify(reqInfoRes.body));
  }
  console.log('✓ Admin requested corrections. Status: ' + reqInfoRes.body.status + ', Reason: ' + reqInfoRes.body.actionRequiredReason);

  // 14. Provider inspects action required and resubmits
  const provCheckRes = await request('GET', '/provider/verification/application', null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (provCheckRes.body.status !== 'ACTION_REQUIRED') {
    throw new Error('Provider does not see ACTION_REQUIRED');
  }
  console.log('✓ Provider observes ACTION_REQUIRED status and admin explanation');

  // Provider resubmits
  const resubmitRes = await request('POST', '/provider/verification/submit', {
    providerType: 'INDIVIDUAL',
    legalName: 'Ramesh Kumar Sharma',
    idType: 'PAN',
    idNumber: 'ABCPS1234F',
    privacyConsent: true,
    serviceAddress: '123 Main Road, Indiranagar',
    city: 'Bengaluru',
    locality: 'Indiranagar',
    pincode: '560038'
  }, {
    'Authorization': 'Bearer ' + provToken
  });
  if (resubmitRes.status !== 200 || resubmitRes.body.status !== 'SUBMITTED') {
    throw new Error('Resubmission failed: ' + JSON.stringify(resubmitRes.body));
  }
  console.log('✓ Provider successfully resubmitted. Status is now: ' + resubmitRes.body.status);

  // 15. Admin updates Checklist: marks all individual items complete
  const checkUpdateRes = await request('PUT', `/admin/verification/applications/${appId}/checklist`, {
    identityDocReceived: true,
    identityDocReadable: true,
    nameAndDetailsReviewed: true,
    identityCheckAuthorized: true,
    contactVerified: true
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (checkUpdateRes.status !== 200 || !checkUpdateRes.body.allChecked) {
    throw new Error('Checklist update failed: ' + JSON.stringify(checkUpdateRes.body));
  }
  console.log('✓ Admin completed all verification checklist items. allChecked: ' + checkUpdateRes.body.allChecked);

  // 16. Admin Approves Application
  const approveRes = await request('POST', `/admin/verification/applications/${appId}/action`, {
    action: 'APPROVE',
    adminNotes: 'Identity document verified and details match account profile.'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (approveRes.status !== 200 || approveRes.body.status !== 'VERIFIED') {
    throw new Error('Approve failed: ' + JSON.stringify(approveRes.body));
  }
  console.log('✓ Application approved! Status: ' + approveRes.body.status);

  // 17. Verify Provider has received VERIFIED privileges
  const providerMeRes = await request('GET', '/provider/profile', null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (providerMeRes.status !== 200 || providerMeRes.body.verificationStatus !== 'VERIFIED') {
    throw new Error('Provider verification status was not updated: ' + JSON.stringify(providerMeRes.body));
  }
  console.log('✓ Provider verification status is now VERIFIED. Booking eligibility unlocked!');

  // 18. Test Secure Document Access:
  // Admin accessing document -> 200
  const adminDocRes = await request('GET', `/verification/documents/${uploadedDocId}/file`, null, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (adminDocRes.status !== 200) throw new Error('Admin doc access failed: ' + adminDocRes.status);
  console.log('✓ Admin successfully retrieved document file (200 OK)');

  // Provider (owner) accessing document -> 200
  const ownerDocRes = await request('GET', `/verification/documents/${uploadedDocId}/file`, null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (ownerDocRes.status !== 200) throw new Error('Owner doc access failed: ' + ownerDocRes.status);
  console.log('✓ Provider owner successfully retrieved document file (200 OK)');

  // Another random user -> 403 Forbidden
  const otherProvEmail = 'other_' + Date.now() + '@example.com';
  const otherReg = await request('POST', '/auth/register', {
    name: 'Other Provider',
    email: otherProvEmail,
    phone: '9123456780',
    password: 'Password@123',
    role: 'PROVIDER',
    businessName: 'Other Works'
  });
  const otherToken = otherReg.body.token;
  const unauthorizedDocRes = await request('GET', `/verification/documents/${uploadedDocId}/file`, null, {
    'Authorization': 'Bearer ' + otherToken
  });
  if (unauthorizedDocRes.status !== 403) {
    throw new Error('Expected 403 Forbidden for unauthorized user accessing document, got: ' + unauthorizedDocRes.status);
  }
  console.log('✓ Security: Unauthorized provider blocked from accessing another provider\'s private document (403 Forbidden)');

  // 19. Check Notifications
  const notifsRes = await request('GET', '/notifications', null, {
    'Authorization': 'Bearer ' + provToken
  });
  if (notifsRes.status !== 200 || !Array.isArray(notifsRes.body)) {
    throw new Error('Failed to fetch provider notifications');
  }
  console.log(`✓ Provider received ${notifsRes.body.length} notifications during verification flow`);
  for (const n of notifsRes.body) {
    console.log(`    - [${n.type}] ${n.title}: ${n.message}`);
  }

  // 20. TEST SHOP / REGISTERED BUSINESS PROVIDER WORKFLOW
  console.log('\n--- Testing Shop / Registered Business Provider Flow ---');
  const bizEmail = 'autopro_' + Date.now() + '@example.com';
  const bizReg = await request('POST', '/auth/register', {
    name: 'Vikram Singh',
    email: bizEmail,
    phone: '9845012345',
    password: 'Password@123',
    role: 'PROVIDER',
    businessName: 'AutoPro Garage & Body Shop'
  });
  const bizToken = bizReg.body.token;
  console.log('✓ Registered Shop/Business Provider: ' + bizEmail);

  // Save draft with business details
  const bizDraft = await request('POST', '/provider/verification/draft', {
    providerType: 'BUSINESS',
    legalName: 'Vikram Singh',
    idType: 'PASSPORT',
    idNumber: 'Z1234567',
    privacyConsent: true,
    serviceAddress: 'Shop #14, Service Road, Koramangala',
    city: 'Bengaluru',
    locality: 'Koramangala 4th Block',
    pincode: '560034',
    businessName: 'AutoPro Garage & Body Shop',
    businessType: 'Automobile Repair Garage',
    businessAddress: 'Shop #14, Service Road, Koramangala',
    businessPhone: '080-25539988',
    businessDescription: 'Full-service multi-brand automotive repair and denting-painting garage.',
    operatingHours: 'Mon - Sat: 9:00 AM - 7:00 PM',
    servicesOffered: 'Car Servicing, Brake Repair, Denting & Painting, Engine Diagnostics',
    serviceAreas: 'Koramangala, HSR Layout, BTM Layout',
    locationProofType: 'RENT_AGREEMENT'
  }, {
    'Authorization': 'Bearer ' + bizToken
  });
  if (bizDraft.status !== 200 || bizDraft.body.providerType !== 'BUSINESS') {
    throw new Error('Biz draft failed: ' + JSON.stringify(bizDraft.body));
  }
  const bizAppId = bizDraft.body.id;
  console.log('✓ Saved Business Draft successfully. Application ID: ' + bizAppId);

  // Upload Required Business Documents
  // 1. Identity proof
  const bizIdDoc = buildMultipart({ type: 'IDENTITY_PROOF' }, {
    name: 'file',
    filename: 'passport.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  await request('POST', '/provider/verification/documents', bizIdDoc.buffer, {
    'Authorization': 'Bearer ' + bizToken,
    'Content-Type': bizIdDoc.contentType
  });

  // 2. Shop exterior photo
  const extPhoto = buildMultipart({ type: 'SHOP_EXTERIOR' }, {
    name: 'file',
    filename: 'exterior.jpg',
    contentType: 'image/jpeg',
    buffer: createFakeJpeg()
  });
  await request('POST', '/provider/verification/documents', extPhoto.buffer, {
    'Authorization': 'Bearer ' + bizToken,
    'Content-Type': extPhoto.contentType
  });

  // 3. Shop signboard photo
  const signPhoto = buildMultipart({ type: 'SHOP_SIGNBOARD' }, {
    name: 'file',
    filename: 'signboard.png',
    contentType: 'image/png',
    buffer: Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52])
  });
  await request('POST', '/provider/verification/documents', signPhoto.buffer, {
    'Authorization': 'Bearer ' + bizToken,
    'Content-Type': signPhoto.contentType
  });

  // 4. Location proof: Valid lease/rent agreement PDF
  const leasePdf = buildMultipart({ type: 'LOCATION_PROOF' }, {
    name: 'file',
    filename: 'commercial_lease_agreement.pdf',
    contentType: 'application/pdf',
    buffer: createFakePdf()
  });
  const leaseRes = await request('POST', '/provider/verification/documents', leasePdf.buffer, {
    'Authorization': 'Bearer ' + bizToken,
    'Content-Type': leasePdf.contentType
  });
  if (leaseRes.status !== 200) throw new Error('PDF lease upload failed: ' + JSON.stringify(leaseRes.body));
  console.log('✓ Uploaded all business docs (Identity, Exterior, Signboard, Lease Agreement PDF)');

  // Submit business application
  const bizSubmit = await request('POST', '/provider/verification/submit', {
    providerType: 'BUSINESS',
    legalName: 'Vikram Singh',
    idType: 'PASSPORT',
    idNumber: 'Z1234567',
    privacyConsent: true,
    serviceAddress: 'Shop #14, Service Road, Koramangala',
    city: 'Bengaluru',
    locality: 'Koramangala 4th Block',
    pincode: '560034',
    businessName: 'AutoPro Garage & Body Shop',
    businessType: 'Automobile Repair Garage',
    businessAddress: 'Shop #14, Service Road, Koramangala',
    businessPhone: '080-25539988',
    businessDescription: 'Full-service multi-brand automotive repair and denting-painting garage.',
    operatingHours: 'Mon - Sat: 9:00 AM - 7:00 PM',
    servicesOffered: 'Car Servicing, Brake Repair, Denting & Painting, Engine Diagnostics',
    serviceAreas: 'Koramangala, HSR Layout, BTM Layout',
    locationProofType: 'RENT_AGREEMENT'
  }, {
    'Authorization': 'Bearer ' + bizToken
  });
  if (bizSubmit.status !== 200 || bizSubmit.body.status !== 'SUBMITTED') {
    throw new Error('Biz submit failed: ' + JSON.stringify(bizSubmit.body));
  }
  console.log('✓ Business application submitted with reference: ' + bizSubmit.body.referenceNumber);

  // Admin Starts Review
  await request('POST', `/admin/verification/applications/${bizAppId}/action`, { action: 'START_REVIEW' }, {
    'Authorization': 'Bearer ' + adminToken
  });

  // Admin tests rejection
  const rejectRes = await request('POST', `/admin/verification/applications/${bizAppId}/action`, {
    action: 'REJECT',
    reason: 'Signboard image was unclear and contact number could not be reached.',
    adminNotes: 'Attempted phone call at 080-25539988, no answer'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (rejectRes.status !== 200 || rejectRes.body.status !== 'REJECTED') {
    throw new Error('Reject failed: ' + JSON.stringify(rejectRes.body));
  }
  console.log('✓ Application rejection recorded with audit trail. Rejection reason: ' + rejectRes.body.rejectionReason);

  // Provider resubmits after correction
  await request('POST', '/provider/verification/submit', {
    providerType: 'BUSINESS',
    legalName: 'Vikram Singh',
    idType: 'PASSPORT',
    idNumber: 'Z1234567',
    privacyConsent: true,
    serviceAddress: 'Shop #14, Service Road, Koramangala',
    city: 'Bengaluru',
    locality: 'Koramangala 4th Block',
    pincode: '560034',
    businessName: 'AutoPro Garage & Body Shop',
    businessType: 'Automobile Repair Garage',
    businessAddress: 'Shop #14, Service Road, Koramangala',
    businessPhone: '9845012345',
    businessDescription: 'Full-service multi-brand automotive repair and denting-painting garage.',
    operatingHours: 'Mon - Sat: 9:00 AM - 7:00 PM',
    servicesOffered: 'Car Servicing, Brake Repair, Denting & Painting, Engine Diagnostics',
    serviceAreas: 'Koramangala, HSR Layout, BTM Layout',
    locationProofType: 'RENT_AGREEMENT'
  }, {
    'Authorization': 'Bearer ' + bizToken
  });

  // Admin checks all business items
  const bizChecklist = await request('PUT', `/admin/verification/applications/${bizAppId}/checklist`, {
    identityDocReceived: true,
    identityDocReadable: true,
    nameAndDetailsReviewed: true,
    identityCheckAuthorized: true,
    businessInfoReviewed: true,
    shopPhotosReviewed: true,
    businessAddressReviewed: true,
    locationProofReviewed: true,
    contactVerified: true
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (!bizChecklist.body.allChecked) throw new Error('Biz checklist not complete');
  console.log('✓ Admin checked all 9 business checklist items. allChecked: true');

  // Admin approves business application
  const bizApprove = await request('POST', `/admin/verification/applications/${bizAppId}/action`, {
    action: 'APPROVE',
    adminNotes: 'Verified commercial lease deed, GST records, and shop premises photographs.'
  }, {
    'Authorization': 'Bearer ' + adminToken
  });
  if (bizApprove.status !== 200 || bizApprove.body.status !== 'VERIFIED') {
    throw new Error('Biz approve failed: ' + JSON.stringify(bizApprove.body));
  }
  console.log('✓ Business application approved! Status: VERIFIED');

  console.log('\n======================================================');
  console.log('🎉 ALL BACKEND INDIVIDUAL & BUSINESS TESTS PASSED! 🎉');
  console.log('======================================================\n');
}

run().catch(err => {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
});
