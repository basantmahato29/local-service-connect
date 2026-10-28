const http = require('http');

function get(path) {
  return new Promise((resolve, reject) => {
    http.get(`http://localhost:8080${path}`, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, data: JSON.parse(data) });
        } catch (e) {
          resolve({ status: res.statusCode, raw: data });
        }
      });
    }).on('error', reject);
  });
}

async function runTests() {
  console.log('=== VERIFYING DEMO PROVIDERS AND SUBCATEGORIES ===\n');

  // 1. Check /api/services
  const servicesRes = await get('/api/services');
  console.log(`[1] /api/services status: ${servicesRes.status}`);
  console.log(`Total services returned: ${servicesRes.data.length}`);
  const serviceNames = servicesRes.data.map(s => s.name);
  console.log(`Services present:`, serviceNames);

  const requiredCategories = [
    'Electrician',
    'Plumbing',
    'AC repair and servicing',
    'Home cleaning',
    'Salon at home',
    'Appliance repair',
    'Carpentry',
    'Painting',
    'Pest control',
    'RO and water purifier repair',
    'Washing machine repair',
    'Refrigerator repair',
    'Geyser repair',
    'Laundry and dry cleaning',
    'Bathroom cleaning',
    'Kitchen cleaning',
    'Furniture assembly',
    'CCTV installation'
  ];

  let missing = [];
  for (const cat of requiredCategories) {
    if (!serviceNames.some(s => s.toLowerCase() === cat.toLowerCase())) {
      missing.push(cat);
    }
  }

  if (missing.length === 0) {
    console.log(`PASS: All 18 required categories are present in backend database!`);
  } else {
    console.error(`FAIL: Missing categories:`, missing);
  }

  // 2. Check /api/providers
  const providersRes = await get('/api/providers');
  console.log(`\n[2] /api/providers status: ${providersRes.status}`);
  console.log(`Total providers returned: ${providersRes.data.length}`);

  const demoProviders = providersRes.data.filter(p => p.isDemo === true || (p.businessName && p.businessName.includes('[DEMO]')));
  console.log(`Demo providers identified: ${demoProviders.length}`);

  // 3. Verify multiple providers per category (preferably 5-10 where appropriate)
  console.log(`\n[3] Checking provider coverage per required category:`);
  let categoryCounts = {};
  for (const cat of requiredCategories) {
    const sObj = servicesRes.data.find(s => s.name.toLowerCase() === cat.toLowerCase());
    if (sObj) {
      const matchRes = await get(`/api/providers?serviceId=${sObj.id}`);
      categoryCounts[cat] = matchRes.data ? matchRes.data.length : 0;
      console.log(`- ${cat} (ID: ${sObj.id}): ${categoryCounts[cat]} providers`);
    } else {
      console.log(`- ${cat}: SERVICE NOT FOUND`);
    }
  }

  // 4. Inspect sample demo provider details (subservices, portfolio, work videos, isDemo flag)
  const sample = demoProviders[0];
  console.log(`\n[4] Sample Demo Provider Details:`);
  console.log(`- ID: ${sample.id}`);
  console.log(`- Business Name: ${sample.businessName}`);
  console.log(`- Provider Name: ${sample.providerName}`);
  console.log(`- isDemo flag: ${sample.isDemo}`);
  console.log(`- Approx Price: ₹${sample.approxPrice}`);
  console.log(`- Rating: ${sample.rating} (${sample.reviewCount} reviews)`);
  console.log(`- Formatted Address: ${sample.formattedAddress}`);
  console.log(`- Profile Image: ${sample.profileImage ? 'Present (' + sample.profileImage.substring(0, 40) + '...)' : 'MISSING'}`);
  console.log(`- Subservices count: ${sample.subServices ? sample.subServices.length : 0}`);
  if (sample.subServices && sample.subServices.length > 0) {
    console.log(`  Sample subservice: ${sample.subServices[0].name} (₹${sample.subServices[0].price})`);
  }
  console.log(`- Portfolio count: ${sample.portfolioItems ? sample.portfolioItems.length : 0}`);
  console.log(`- Work Videos count: ${sample.workVideos ? sample.workVideos.length : 0}`);

  // 5. Test Geolocation Nearby Matching in Ranchi localities
  const nearbyLalpur = await get('/api/providers/nearby?latitude=23.3685&longitude=85.3340&radiusKm=10');
  console.log(`\n[5] /api/providers/nearby in Lalpur (10 km radius): ${nearbyLalpur.data.length} providers found`);

  console.log('\n=== ALL VERIFICATION CHECKS COMPLETED ===');
}

runTests().catch(console.error);
