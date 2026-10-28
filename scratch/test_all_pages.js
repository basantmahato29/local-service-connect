const { spawn } = require('child_process');
const http = require('http');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const port = 9225;

const pages = [
  '/',
  '/index.html',
  '/services.html',
  '/providers.html',
  '/provider-home.html'
];

const edge = spawn(edgePath, [
  '--headless=new',
  '--disable-gpu',
  `--remote-debugging-port=${port}`,
  'http://127.0.0.1:8080/index.html'
]);

async function run() {
  await new Promise(r => setTimeout(r, 2000));
  
  http.get(`http://127.0.0.1:${port}/json`, res => {
    let raw = '';
    res.on('data', chunk => raw += chunk);
    res.on('end', async () => {
      const tabs = JSON.parse(raw);
      const tab = tabs.find(t => t.url && t.url.includes('8080'));
      if (!tab) {
        console.error('No tab found with 8080. Available:', tabs.map(t => t.url));
        edge.kill();
        return;
      }

      const ws = new WebSocket(tab.webSocketDebuggerUrl);
      let id = 1;
      const send = (method, params = {}) => new Promise(resolve => {
        const curId = id++;
        const handler = (evt) => {
          const m = JSON.parse(evt.data);
          if (m.id === curId) {
            ws.removeEventListener('message', handler);
            resolve(m.result);
          }
        };
        ws.addEventListener('message', handler);
        ws.send(JSON.stringify({ id: curId, method, params }));
      });

      ws.onopen = async () => {
        await send('Runtime.enable');
        await send('Page.enable');

        for (const p of pages) {
          const url = `http://127.0.0.1:8080${p}`;
          await send('Page.navigate', { url });
          await new Promise(r => setTimeout(r, 1200));

          const res = await send('Runtime.evaluate', {
            expression: `({
              url: window.location.pathname,
              navExists: !!document.getElementById('nav'),
              mainSiteNavExists: !!document.getElementById('mainSiteNav') || !!document.getElementById('adminSiteNav'),
              navHeight: (document.getElementById('mainSiteNav') || document.getElementById('adminSiteNav') || {}).offsetHeight || 0,
              brandText: (document.querySelector('.brand') || {}).textContent || null
            })`,
            returnByValue: true
          });

          console.log(`Page [${p}]:`, JSON.stringify(res.result.value));
        }

        ws.close();
        edge.kill();
        process.exit(0);
      };
    });
  }).on('error', err => {
    console.error(err);
    edge.kill();
  });
}

run();
