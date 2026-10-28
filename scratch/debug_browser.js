const { spawn } = require('child_process');
const http = require('http');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const port = 9222;

const edge = spawn(edgePath, [
  '--headless=new',
  '--disable-gpu',
  `--remote-debugging-port=${port}`,
  'http://127.0.0.1:8080/index.html'
]);

async function main() {
  await new Promise(r => setTimeout(r, 2000));
  
  http.get(`http://127.0.0.1:${port}/json`, res => {
    let raw = '';
    res.on('data', chunk => raw += chunk);
    res.on('end', async () => {
      const tabs = JSON.parse(raw);
      const tab = tabs.find(t => t.url.includes('8080')) || tabs[0];
      if (!tab || !tab.webSocketDebuggerUrl) {
        console.error('No tab ws found');
        edge.kill();
        return;
      }

      console.log('Connecting to:', tab.webSocketDebuggerUrl);
      const ws = new WebSocket(tab.webSocketDebuggerUrl);
      let id = 1;
      const send = (method, params = {}) => {
        ws.send(JSON.stringify({ id: id++, method, params }));
      };

      ws.onopen = () => {
        console.log('WS connected. Enabling domains...');
        send('Runtime.enable');
        send('Console.enable');
        send('Log.enable');

        setTimeout(() => {
          send('Runtime.evaluate', {
            expression: `({
              navHtml: document.getElementById('nav') ? document.getElementById('nav').innerHTML.slice(0, 100) : null,
              hasMainSiteNav: !!document.getElementById('mainSiteNav'),
              navbarFn: typeof navbar,
              apiBase: typeof API_BASE,
              title: document.title,
              errors: window.__errors || []
            })`,
            returnByValue: true
          });
        }, 3000);
      };

      ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.method === 'Runtime.exceptionThrown') {
          console.error('[EXCEPTION]', JSON.stringify(msg.params.exceptionDetails));
        } else if (msg.method === 'Console.messageAdded') {
          console.log('[CONSOLE]', msg.params.message.level, msg.params.message.text);
        } else if (msg.method === 'Runtime.consoleAPICalled') {
          console.log('[CONSOLE API]', msg.params.type, msg.params.args.map(a => a.value || a.description).join(' '));
        }

        if (msg.id && msg.result && msg.result.result) {
          console.log('[EVAL RESULT]:', JSON.stringify(msg.result.result.value, null, 2));
          ws.close();
          edge.kill();
          process.exit(0);
        }
      };

      ws.onerror = (err) => {
        console.error('WS error:', err);
        edge.kill();
      };
    });
  }).on('error', (e) => {
    console.error('HTTP get error:', e);
    edge.kill();
  });
}

main();
