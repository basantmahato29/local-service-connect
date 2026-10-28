const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

function getFiles(dir, filter) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    file = path.join(dir, file);
    const stat = fs.statSync(file);
    if (stat && stat.isDirectory()) {
      results = results.concat(getFiles(file, filter));
    } else if (filter(file)) {
      results.push(file);
    }
  });
  return results;
}

const jsFiles = getFiles('frontend', f => f.endsWith('.js'));
console.log('Testing ' + jsFiles.length + ' JS files...');
let hasError = false;
for (const file of jsFiles) {
  try {
    execSync(`node -c "${file}"`);
  } catch (err) {
    console.error('JS Syntax Error in ' + file + ':\n', err.stderr ? err.stderr.toString() : err.message);
    hasError = true;
  }
}

const htmlFiles = getFiles('frontend', f => f.endsWith('.html'));
console.log('Testing inline scripts in ' + htmlFiles.length + ' HTML files...');
for (const file of htmlFiles) {
  const content = fs.readFileSync(file, 'utf8');
  const matches = content.matchAll(/<script(?![^>]*src=)[^>]*>([\s\S]*?)<\/script>/gi);
  let idx = 1;
  for (const m of matches) {
    const code = m[1].trim();
    if (!code) continue;
    const tmp = path.join('scratch', 'tmp_' + path.basename(file, '.html') + '_' + idx + '.js');
    fs.writeFileSync(tmp, code);
    try {
      execSync(`node -c "${tmp}"`);
    } catch (err) {
      console.error('Syntax Error in ' + file + ' script #' + idx + ':\n', err.stderr ? err.stderr.toString() : err.message);
      hasError = true;
    } finally {
      if (fs.existsSync(tmp)) fs.unlinkSync(tmp);
    }
    idx++;
  }
}

if (!hasError) {
  console.log('ALL JS and HTML inline scripts passed syntax check with 0 errors!');
} else {
  process.exit(1);
}

