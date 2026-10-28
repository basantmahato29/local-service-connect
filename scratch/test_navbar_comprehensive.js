const { spawn } = require('child_process');
const http = require('http');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const port = 9228;

const edge = spawn(edgePath, [
  '--headless=new',
  '--disable-gpu',
  '--window-size=1280,800',
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
      const tab = tabs.find(t => t.url.includes('8080')) || tabs[0];
      if (!tab) {
        console.error('No tab found');
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
        console.log('WS connected. Running comprehensive tests...');
        await send('Runtime.enable');
        await send('Page.enable');
        await send('DOM.enable');

        // Test 1: Check document flow and layout positions
        const layoutEval = await send('Runtime.evaluate', {
          expression: `(() => {
            const nav = document.getElementById('mainSiteNav');
            const hero = document.querySelector('header.hero');
            if (!nav || !hero) return { error: 'nav or hero missing' };
            const navRect = nav.getBoundingClientRect();
            const heroRect = hero.getBoundingClientRect();
            return {
              navExists: true,
              navId: nav.id,
              navClass: nav.className,
              navTop: navRect.top,
              navBottom: navRect.bottom,
              navHeight: navRect.height,
              heroTop: heroRect.top,
              heroBottom: heroRect.bottom,
              isNavAboveHero: heroRect.top >= navRect.bottom - 1,
              isNavVisible: navRect.height > 0 && window.getComputedStyle(nav).display !== 'none' && window.getComputedStyle(nav).visibility !== 'hidden'
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 1: Layout & Positioning ---');
        console.log(JSON.stringify(layoutEval.result.value, null, 2));

        // Test 2: Unauthenticated State Controls
        const unauthEval = await send('Runtime.evaluate', {
          expression: `(() => {
            localStorage.clear();
            navbar();
            const nav = document.getElementById('mainSiteNav');
            const brand = nav.querySelector('.brand');
            const links = Array.from(nav.querySelectorAll('.navbar-center .nav-link')).map(a => ({
              text: a.textContent.trim(),
              href: a.getAttribute('href'),
              isActive: a.classList.contains('active')
            }));
            const loginBtn = nav.querySelector('a[href="/login.html"]');
            const signupBtn = nav.querySelector('a[href="/register.html"]');
            const mobileToggler = document.getElementById('btnMobileNavToggle');
            return {
              brandText: brand ? brand.textContent.trim() : null,
              brandHref: brand ? brand.getAttribute('href') : null,
              links,
              hasLogin: !!loginBtn,
              hasSignup: !!signupBtn,
              hasMobileToggler: !!mobileToggler
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 2: Unauthenticated State Controls ---');
        console.log(JSON.stringify(unauthEval.result.value, null, 2));

        // Test 3: Authenticated Customer State
        const customerEval = await send('Runtime.evaluate', {
          expression: `(() => {
            localStorage.setItem('lsc_token', 'mock.token.jwt');
            localStorage.setItem('lsc_role', 'CUSTOMER');
            localStorage.setItem('lsc_name', 'Rahul Sharma');
            localStorage.setItem('lsc_id', '101');
            navbar();
            const nav = document.getElementById('mainSiteNav');
            const links = Array.from(nav.querySelectorAll('.navbar-center .nav-link')).map(a => ({
              text: a.textContent.trim(),
              href: a.getAttribute('href')
            }));
            const notifBtn = document.getElementById('navNotifBtn');
            const userBtn = document.getElementById('navUserDropdownBtn');
            const userName = nav.querySelector('.user-name-text');
            return {
              customerLinks: links,
              hasNotifBtn: !!notifBtn,
              hasUserBtn: !!userBtn,
              userName: userName ? userName.textContent.trim() : null
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 3: Authenticated Customer State ---');
        console.log(JSON.stringify(customerEval.result.value, null, 2));

        // Test 4: Authenticated Provider State
        const providerEval = await send('Runtime.evaluate', {
          expression: `(() => {
            localStorage.setItem('lsc_token', 'mock.token.jwt');
            localStorage.setItem('lsc_role', 'PROVIDER');
            localStorage.setItem('lsc_name', 'Sharma Electricals');
            localStorage.setItem('lsc_id', '201');
            navbar();
            const nav = document.getElementById('mainSiteNav');
            const dashboardBtn = nav.querySelector('.btn-navbar-dashboard');
            const links = Array.from(nav.querySelectorAll('.navbar-center .nav-link')).map(a => ({
              text: a.textContent.trim(),
              href: a.getAttribute('href')
            }));
            return {
              providerLinks: links,
              hasDashboardBtn: !!dashboardBtn
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 4: Authenticated Provider State ---');
        console.log(JSON.stringify(providerEval.result.value, null, 2));

        // Test 5: Responsive Drawer Test
        const drawerEval = await send('Runtime.evaluate', {
          expression: `(() => {
            const toggle = document.getElementById('btnMobileNavToggle');
            const drawer = document.getElementById('mobileNavDrawer');
            const backdrop = document.getElementById('mobileNavBackdrop');
            const closeBtn = document.getElementById('btnCloseMobileNav');
            if (toggle) toggle.click();
            const opened = drawer.classList.contains('show') && backdrop.classList.contains('show');
            if (closeBtn) closeBtn.click();
            const closed = !drawer.classList.contains('show') && !backdrop.classList.contains('show');
            return {
              toggleExists: !!toggle,
              drawerOpensOnToggle: opened,
              drawerClosesOnCloseBtn: closed
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 5: Mobile Drawer Interactions ---');
        console.log(JSON.stringify(drawerEval.result.value, null, 2));

        // Test 6: Scroll Behavior
        const scrollEval = await send('Runtime.evaluate', {
          expression: `(() => {
            window.scrollTo(0, 100);
            window.dispatchEvent(new Event('scroll'));
            const nav = document.getElementById('mainSiteNav');
            const isScrolled = nav.classList.contains('scrolled');
            window.scrollTo(0, 0);
            window.dispatchEvent(new Event('scroll'));
            const isReset = !nav.classList.contains('scrolled');
            return {
              scrollY: window.scrollY,
              scrolledClassAdded: isScrolled,
              scrolledClassRemovedOnTop: isReset
            };
          })()`,
          returnByValue: true
        });

        console.log('\n--- TEST 6: Scroll Behavior ---');
        console.log(JSON.stringify(scrollEval.result.value, null, 2));

        // Cleanup
        ws.close();
        edge.kill();
        process.exit(0);
      };
    });
  }).on('error', err => {
    console.error('Error connecting to Edge:', err);
    edge.kill();
    process.exit(1);
  });
}

run();
