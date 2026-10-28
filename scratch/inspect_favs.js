const fs = require('fs');

const accountHtml = fs.readFileSync('frontend/my-account.html', 'utf8');
const favStart = accountHtml.indexOf('id="tab-favorites"');
if (favStart !== -1) {
  console.log('--- tab-favorites section in my-account.html ---');
  console.log(accountHtml.substring(favStart, favStart + 1500));
}

const apiJs = fs.readFileSync('frontend/js/api.js', 'utf8');
const lines = apiJs.split('\n');
console.log('--- matches in api.js ---');
lines.forEach((l, idx) => {
  if (l.toLowerCase().includes('favorite')) {
    console.log(`${idx + 1}: ${l.trim()}`);
  }
});
