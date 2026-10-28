const fs = require('fs');

const accountHtml = fs.readFileSync('frontend/my-account.html', 'utf8');
const tabMatches = [...accountHtml.matchAll(/id="(tab-[^"]+)"/g)].map(m => m[1]);
console.log('Tab pane IDs in my-account.html:', tabMatches);

const navMatches = [...accountHtml.matchAll(/data-tab="([^"]+)"/g)].map(m => m[1]);
console.log('Nav data-tab values in my-account.html:', navMatches);

const apiJs = fs.readFileSync('frontend/js/api.js', 'utf8');
const hasFavoriteInApi = apiJs.includes('favorite') || apiJs.includes('Favorite');
console.log('api.js has favorite references:', hasFavoriteInApi);
