#!/usr/bin/env python3
"""冒烟测试用的假树维 eams（只在 CI 的模拟器测试里跑）：
首页是没有菜单的空架子，课表只能靠导入按钮自己请求 courseTableForStd.action → courseTableForStd!courseTable.action 拿到。
页面是 app/src/test/resources/jw/eams_uestc/ 里手写的样例，不是真实学校的页面。用法：python3 tools/fake_eams.py 8765"""
import os, sys, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'test', 'resources', 'jw', 'eams_uestc')
def page(n): return open(os.path.join(D, n), 'rb').read()

class H(BaseHTTPRequestHandler):
    def send(self, body, code=200):
        self.send_response(code); self.send_header('Content-Type', 'text/html;charset=UTF-8'); self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        p = urllib.parse.urlparse(self.path).path
        if p in ('/eams/home!index.action', '/eams/home.action', '/eams/'): self.send(page('home.html'))
        elif p == '/eams/courseTableForStd.action': self.send(page('std.html'))
        elif p == '/eams/courseTableForStd!courseTable.action': self.send('<html>use POST</html>'.encode())
        else: self.send(b'not found', 404)
    def do_POST(self):
        p = urllib.parse.urlparse(self.path).path
        n = int(self.headers.get('Content-Length') or 0); q = urllib.parse.parse_qs(self.rfile.read(n).decode(), keep_blank_values=True)
        g = lambda k: (q.get(k) or [''])[0]
        ok = p == '/eams/courseTableForStd!courseTable.action' and g('ignoreHead') == '1' and g('setting.kind') == 'std' and g('ids') == '987654' and g('semester.id') == '523'
        sys.stderr.write('fake eams POST %s ok=%s %s\n' % (p, ok, q)); sys.stderr.flush()
        self.send(page('table.html') if ok else '<html><body>没有课程</body></html>'.encode())

if __name__ == '__main__':
    ThreadingHTTPServer(('127.0.0.1', int(sys.argv[1]) if len(sys.argv) > 1 else 8765), H).serve_forever()
