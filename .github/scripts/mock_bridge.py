#!/usr/bin/env python3
"""测试用的假电脑端（bridge）。

CI 里没有真实的 DeepSeek Harness 桌面端可用，所以起一个只实现"配对"这一条的
最小服务，用来验证 iOS 外壳的三件事：
  1. 外壳能不能把配对码填进页面（原生扫码 → JS 注入）
  2. 页面配对成功后，外壳能不能察觉"已配对"并落盘
  3. 失败/重试态能不能正确显示

只跑在 runner 的 127.0.0.1 上，不联外网。
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CODE = "TEST-1234"

PAGE = """<!doctype html>
<html lang="zh-CN" data-theme="dark"><head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
<title>假电脑端 · 配对</title>
<style>
  *{box-sizing:border-box}
  body{margin:0;height:100%;background:#11130F;color:#EEE9DC;
       font:15px/1.6 -apple-system,system-ui,"PingFang SC",sans-serif}
  #root{display:flex;flex-direction:column;height:100%}
  header{padding:12px;padding-top:calc(12px + env(safe-area-inset-top));
         border-bottom:1px solid #34392F;display:flex;gap:8px;align-items:center}
  header b{font-size:15px}
  .tag{margin-left:auto;font:600 11px ui-monospace;color:#D9B879;
       border:1px solid rgba(217,184,121,.5);border-radius:5px;padding:2px 6px}
  main{flex:1;overflow:auto;padding:22px 18px}
  h1{font-size:24px;font-weight:500;margin:0 0 8px}
  p{color:#B9B9AA;font-size:13.5px;margin:0 0 18px}
  label{display:block;font-size:11.5px;color:#96998B;margin:14px 0 6px}
  input{width:100%;height:46px;border-radius:10px;border:1px solid #34392F;
        background:#0D100C;color:#EEE9DC;padding:0 12px;font:15px ui-monospace}
  button{margin-top:18px;width:100%;height:50px;border:0;border-radius:13px;
         background:#D9B879;color:#24271F;font:600 16px -apple-system}
  #sessions{display:none}
  .row{display:flex;gap:10px;padding:14px 0;border-bottom:1px solid #34392F}
  .dot{width:7px;height:7px;border-radius:50%;background:#9CBB99;margin-top:8px}
  .t{font-size:14.5px}.s{font-size:12px;color:#96998B}
</style></head>
<body><div id="root">
  <header><b>DeepSeek Harness</b><span class="tag">MOCK BRIDGE</span></header>
  <main>
    <div id="pairing">
      <h1>配对这台手机</h1>
      <p>测试用假电脑端：只实现配对接口，用来验证 iOS 外壳的原生配对链路。</p>
      <label>配对码</label>
      <input id="pair-code" placeholder="XXXX-XXXX" autocapitalize="characters" />
      <label>设备名称</label>
      <input id="pair-name" value="我的手机" />
      <button id="pair-go">配对</button>
    </div>
    <div id="sessions">
      <h1 style="font-size:20px">会话</h1>
      <div class="row"><div class="dot"></div><div><div class="t">激光焊接参数整理</div>
        <div class="s">配对成功 · 已连上电脑端</div></div></div>
      <div class="row"><div><div class="t">金相图像拼接方案</div>
        <div class="s">空闲</div></div></div>
      <div class="row"><div><div class="t">周报：桥接协议复盘</div>
        <div class="s">空闲</div></div></div>
    </div>
  </main>
</div>
<script>
  var btn = document.getElementById('pair-go');
  btn.addEventListener('click', function () {
    var code = document.getElementById('pair-code').value.trim();
    fetch('/mobile/pair', {
      method: 'POST',
      headers: {'content-type': 'application/json'},
      body: JSON.stringify({code: code, deviceName: 'iPhone', platform: 'ios'})
    }).then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function () {
        var box = document.getElementById('pairing');
        if (box) { box.remove(); }
        document.getElementById('sessions').style.display = 'block';
      })
      .catch(function () { alert('配对码不对'); });
  });
</script>
</body></html>
"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # 让日志进 CI 输出
        sys.stderr.write("mock-bridge: " + fmt % args + "\n")

    def _send(self, code, body, ctype="text/html; charset=utf-8"):
        raw = body.encode("utf-8")
        self.send_response(code)
        self.send_header("content-type", ctype)
        self.send_header("content-length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path.startswith("/mobile"):
            self._send(200, PAGE)
        else:
            self._send(404, "not found")

    def do_POST(self):
        length = int(self.headers.get("content-length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            body = json.loads(raw.decode("utf-8"))
        except Exception:
            body = {}
        if self.path == "/mobile/pair":
            if body.get("code") == CODE:
                self._send(200, json.dumps({"token": "mock-token-abc123"}), "application/json")
            else:
                self._send(401, json.dumps({"error": "bad code"}), "application/json")
            return
        self._send(404, "not found")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    print("mock bridge listening on 127.0.0.1:%d" % port, flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
