const fs = require('fs');

const accountHtml = fs.readFileSync('frontend/my-account.html', 'utf8');
const lines = accountHtml.split('\n');
console.log('--- matches for favoritesGrid in my-account.html ---');
lines.forEach((l, idx) => {
  if (l.includes('favoritesGrid') || l.includes('loadFavorites') || l.includes('tab === \'favorites\'') || l.includes('tab === "favorites"')) {
    console.log(`${idx + 1}: ${l.trim()}`);
  }
});
