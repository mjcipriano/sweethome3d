// node browser.mjs <url> <steps.mjs>: drives a page in headless Chrome through its debugging protocol.
// --no-sandbox is for containers and WSL, where the sandbox of Chrome can't start; the page is a local one.
import {spawn} from 'node:child_process';
import fs from 'node:fs';
const url = process.argv[2];
const out = process.env.SHOTS_OUT + '/';
const port = 9340;
const chrome = spawn('google-chrome', ['--headless=new', '--no-sandbox', '--remote-debugging-port=' + port,
  '--enable-unsafe-swiftshader', '--use-angle=swiftshader', '--window-size=1100,680', '--hide-scrollbars',
  '--user-data-dir=' + process.env.SHOTS_WORK + '/chrome-profile', 'about:blank'], {stdio: 'ignore'});
const sleep = ms => new Promise(r => setTimeout(r, ms));
let ws, id = 0; const pending = new Map(); const logs = [];
async function connect() {
  for (let i = 0; i < 40; i++) {
    try {
      const targets = await (await fetch('http://127.0.0.1:' + port + '/json')).json();
      const page = targets.find(t => t.type === 'page');
      ws = new WebSocket(page.webSocketDebuggerUrl);
      await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });
      ws.onmessage = e => {
        const m = JSON.parse(e.data);
        if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result || m.error); pending.delete(m.id); }
        if (m.method === 'Runtime.consoleAPICalled' && (m.params.type === 'error' || m.params.type === 'warning')) logs.push('console: ' + m.params.args.map(a => a.value ?? a.description).join(' ').slice(0, 300));
        if (m.method === 'Log.entryAdded') logs.push('log: ' + m.params.entry.text.slice(0, 200) + ' ' + (m.params.entry.url || ''));
        if (m.method === 'Network.loadingFailed') logs.push('netfail: ' + m.params.errorText);
        if (m.method === 'Runtime.exceptionThrown') logs.push('EXCEPTION: ' + (m.params.exceptionDetails.exception?.description || m.params.exceptionDetails.text));
      };
      return;
    } catch (e) { await sleep(250); }
  }
  throw new Error('no chrome');
}
const send = (method, params = {}) => new Promise(res => { pending.set(++id, res); ws.send(JSON.stringify({id, method, params})); });
const evaluate = async expr => { const r = await send('Runtime.evaluate', {expression: expr, returnByValue: true, awaitPromise: true}); return r.result?.value ?? (r.exceptionDetails ? 'ERR ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text) : undefined); };
const key = (type, code, k) => evaluate(`window.dispatchEvent(new KeyboardEvent('${type}', {code: '${code}', key: '${k || ''}'}))`);
const press = async (code, k) => { await key('keydown', code, k); await key('keyup', code, k); };
const hold = async (code, ms) => { await key('keydown', code); await sleep(ms); await key('keyup', code); };
const shot = async name => { const r = await send('Page.captureScreenshot', {format: 'png'}); fs.writeFileSync(out + name + '.png', Buffer.from(r.data, 'base64')); console.log('shot', name); };
try {
  await connect();
  await send('Runtime.enable'); await send('Page.enable'); await send('Log.enable'); await send('Network.enable');
  await send('Page.navigate', {url});
  const steps = await import(new URL(process.argv[3], import.meta.url));
  await steps.default({evaluate, key, press, hold, shot, sleep, send});
  console.log('LOGS', logs.join(' | ') || 'none');
} finally { chrome.kill(); }
