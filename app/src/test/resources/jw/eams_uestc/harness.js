// 单测用：在 node 里模拟 WebView 的页面环境跑 jwScript("eams")，假的 fetch 按网址返回样例页面。
// 用法：node harness.js <脚本文件> <首页> <课表入口页> <模式 strict|anysem>
const fs = require('fs');
const [script, homeF, stdF, mode] = process.argv.slice(2);
const HOME = fs.readFileSync(homeF, 'utf8'), STD = fs.readFileSync(stdF, 'utf8');
const TABLE = fs.readFileSync(__dirname + '/table.html', 'utf8');
const URL0 = 'https://eams.uestc.edu.cn/eams/home!index.action';
const log = [];
const doc = { documentElement: { outerHTML: HOME }, querySelectorAll: () => [], getElementById: () => null, cookie: '', location: { href: URL0 } };
global.window = global; global.document = doc; window.top = window;
global.location = { href: URL0, host: 'eams.uestc.edu.cn', origin: 'https://eams.uestc.edu.cn' };
const resp = (body, status) => ({ ok: status === 200, status, text: async () => body, headers: { get: () => 'text/html;charset=UTF-8' } });
global.fetch = async (url, o) => {
  o = o || {}; log.push({ url, method: o.method || 'GET', body: o.body || '' });
  if (url === 'https://eams.uestc.edu.cn/eams/courseTableForStd.action') return resp(STD, 200);
  if (url.startsWith('https://eams.uestc.edu.cn/eams/courseTableForStd!courseTable.action')) {
    if ((o.method || 'GET') !== 'POST') return resp('<html>method</html>', 200);
    const q = new URLSearchParams(o.body || '');
    const semOk = mode === 'anysem' ? /^\d+$/.test(q.get('semester.id') || '') : q.get('semester.id') === '523';
    const ok = semOk && q.get('ids') === '987654' && q.get('ignoreHead') === '1' && q.get('setting.kind') === 'std';
    return resp(ok ? TABLE : '<html><body>没有课程</body></html>', 200);
  }
  return resp('', 404);
};
global.ZhishiJw = { post: s => { process.stdout.write(JSON.stringify({ result: JSON.parse(s), log }) + '\n'); } };
eval(fs.readFileSync(script, 'utf8'));
