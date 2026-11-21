// 单测用：在 node 里模拟 WebView 页面跑真正的 jwScript(kind)。
// 用法：node harness.js <脚本文件> <配置 json>
// 配置：{url, home, frames:[文件], timeout, routes:[{method, path, has:[请求体或查询里必须有的片段], file, text, status, hang}]}
// 样例页面都在 test/resources/jw/ 下，是手写的，不是真实学校的页面。
const fs = require('fs'), path = require('path');
const [script, cfgFile] = process.argv.slice(2);
const cfg = JSON.parse(fs.readFileSync(cfgFile, 'utf8'));
const R = path.join(__dirname, '..');
const read = f => fs.readFileSync(path.join(R, f), 'utf8');
const U = new URL(cfg.url);
function fakeDoc(html, href) {
  const valOf = id => {
    let m = html.match(new RegExp('<input[^>]*id=["\']' + id + '["\'][^>]*value=["\']([^"\']*)["\']')) || html.match(new RegExp('<input[^>]*value=["\']([^"\']*)["\'][^>]*id=["\']' + id + '["\']'));
    if (m) return m[1];
    const s = html.match(new RegExp('<select[^>]*id=["\']' + id + '["\'][\\s\\S]*?</select>'));
    if (s) { const o = s[0].match(/<option[^>]*value=["']([^"']*)["'][^>]*selected/i) || s[0].match(/<option[^>]*selected[^>]*value=["']([^"']*)["']/i); if (o) return o[1]; }
    return null;
  };
  const frames = [];
  return {
    documentElement: { outerHTML: html }, location: { href }, cookie: '',
    getElementById: id => { const v = valOf(id); return v == null ? null : { value: v }; },
    querySelectorAll: sel => {
      if (sel.indexOf('password') >= 0) return /type=["']?password/i.test(html) ? [{ offsetParent: {} }] : [];
      if (sel.indexOf('iframe') >= 0 && sel.indexOf('src') < 0) return frames;
      return [];
    },
    _frames: frames,
  };
}
const doc = fakeDoc(cfg.home ? read(cfg.home) : '<html><body></body></html>', cfg.url);
for (const f of cfg.frames || []) doc._frames.push({ contentWindow: { document: fakeDoc(read(f), cfg.url) } });
global.window = global; global.document = doc; window.top = window;
global.location = { href: cfg.url, host: U.host, origin: U.origin };
if (cfg.timeout) window.__jwTimeout = cfg.timeout;
global.DOMParser = class { parseFromString(t) { return fakeDoc(t, cfg.url); } };
const log = [];
const resp = (body, status, url) => ({ ok: status >= 200 && status < 300, status, url, redirected: false, text: async () => body,
  arrayBuffer: async () => new TextEncoder().encode(body).buffer, headers: { get: () => 'text/html;charset=UTF-8' } });
global.fetch = (url, o) => {
  o = o || {}; const u = new URL(url, cfg.url); const m = (o.method || 'GET');
  const q = (o.body || '') + '&' + u.search.slice(1);
  log.push(m + ' ' + u.pathname);
  for (const r of cfg.routes || []) {
    if ((r.method || 'GET') !== m || r.path !== u.pathname) continue;
    if ((r.has || []).some(h => q.indexOf(h) < 0)) continue;
    if (r.hang) return new Promise((res, rej) => { if (o.signal) o.signal.addEventListener('abort', () => { const e = new Error('aborted'); e.name = 'AbortError'; rej(e); }); });
    return Promise.resolve(resp(r.file ? read(r.file) : (r.text || ''), r.status || 200, u.href));
  }
  return Promise.resolve(resp('', 404, u.href));
};
const steps = [];
global.ZhishiJw = { post: s => { process.stdout.write(JSON.stringify({ result: JSON.parse(s), log, steps }) + '\n'); }, step: (t, s) => steps.push(s) };
eval(fs.readFileSync(script, 'utf8'));
