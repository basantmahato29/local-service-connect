const fs = require('fs');

const html = fs.readFileSync('frontend/index.html', 'utf8');
const scriptMatches = html.matchAll(/<script(?![^>]*src=)[^>]*>([\s\S]*?)<\/script>/gi);

let index = 1;
for (const match of scriptMatches) {
  const code = match[1];
  console.log(`Checking script #${index}... (length: ${code.length})`);
  fs.writeFileSync(`scratch/script_${index}.js`, code);
  try {
    require('child_process').execSync(`node -c scratch/script_${index}.js`);
    console.log(`Script #${index} node -c passed!`);
  } catch (e) {
    console.error(`node -c failed on script #${index}:\n`, e.stderr.toString());
  }
  index++;
}
