const fs = require('fs');
const path = require('path');

const frontendDir = path.resolve('frontend');
const files = fs.readdirSync(frontendDir);

console.log('Searching for map / location / modal references in frontend...');
files.forEach(file => {
  if (file.endsWith('.html') || file.endsWith('.js')) {
    const fullPath = path.join(frontendDir, file);
    if (fs.statSync(fullPath).isFile()) {
      const content = fs.readFileSync(fullPath, 'utf8');
      const lines = content.split('\n');
      lines.forEach((line, idx) => {
        if (/leaflet|L\.map|google\.maps|bookingmodal|mapmodal|modal.*map|map.*modal|current.*loc|use.*loc|kiul|lakhisarai/i.test(line)) {
          console.log(`${file}:${idx + 1}: ${line.trim().substring(0, 120)}`);
        }
      });
    }
  }
});

// Also check frontend/js
const jsDir = path.join(frontendDir, 'js');
if (fs.existsSync(jsDir)) {
  fs.readdirSync(jsDir).forEach(file => {
    if (file.endsWith('.js')) {
      const fullPath = path.join(jsDir, file);
      const content = fs.readFileSync(fullPath, 'utf8');
      const lines = content.split('\n');
      lines.forEach((line, idx) => {
        if (/leaflet|L\.map|google\.maps|current.*loc|use.*loc|kiul|lakhisarai/i.test(line)) {
          console.log(`js/${file}:${idx + 1}: ${line.trim().substring(0, 120)}`);
        }
      });
    }
  });
}
