#!/usr/bin/env python3
"""用真实 Chrome 以 iPhone 视口(390x844@2x)渲染桥接网页版并校验。

为什么要 CDP 而不是 --headless --screenshot：
  1) --window-size 是物理像素，配 --force-device-scale-factor=2 会把 CSS 视口砍半（假溢出）；
  2) --virtual-time-budget 会饿死页面里的 IndexedDB/fetch 异步初始化，页面常常白屏；
  3) 结论：用 CDP 的 Emulation.setDeviceMetricsOverride + mobile=True 才是 iPhone 的真实布局。
用法: python scripts/cdp-pwa-check.py [url] [输出png]
"""
import base64, json, os, shutil, socket, struct, subprocess, sys, time, urllib.request
from urllib.parse import urlparse

CHROME = r"C:/Program Files/Google/Chrome/Application/chrome.exe"
URL = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:17731/mobile/"
OUT = sys.argv[2] if len(sys.argv) > 2 else "pwa-ios.png"
UA = ("Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
      "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
prof = pathlib.Path(os.environ.get("TMPDIR", ".")) / "cdp-pwa-prof"
shutil.rmtree(prof, ignore_errors=True)
PORT = 9377
proc = subprocess.Popen([CHROME, "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                         f"--remote-debugging-port={PORT}", "--user-data-dir=" + str(prof), "about:blank"],
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
st = {"buf": b"", "mid": 0}
try:
    ws = None
    for _ in range(60):
        try:
            ws = [t for t in json.load(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json"))
                  if t["type"] == "page"][0]["webSocketDebuggerUrl"]; break
        except Exception: time.sleep(0.3)
    u = urlparse(ws); sock = socket.create_connection((u.hostname, u.port))
    key = base64.b64encode(os.urandom(16)).decode()
    sock.sendall((f"GET {u.path} HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\nUpgrade: websocket\r\n"
                  f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    hdr = b""
    while b"\r\n\r\n" not in hdr: hdr += sock.recv(4096)
    st["buf"] = hdr.split(b"\r\n\r\n", 1)[1]

    def send(method, params=None):
        st["mid"] += 1
        msg = json.dumps({"id": st["mid"], "method": method, "params": params or {}}).encode()
        mask = os.urandom(4); n = len(msg)
        head = b"\x81" + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n))
        sock.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(msg)))
        return st["mid"]

    def recv(want, timeout=60):
        end = time.time() + timeout
        while time.time() < end:
            buf = st["buf"]
            while len(buf) >= 2:
                ln = buf[1] & 0x7F; off = 2
                if ln == 126: ln = struct.unpack(">H", buf[2:4])[0]; off = 4
                elif ln == 127: ln = struct.unpack(">Q", buf[2:10])[0]; off = 10
                if len(buf) < off + ln: break
                payload = buf[off:off + ln]; buf = buf[off + ln:]
                try: data = json.loads(payload)
                except Exception: continue
                if data.get("id") == want: st["buf"] = buf; return data
            st["buf"] = buf
            try:
                part = sock.recv(65536)
                if not part: break
                st["buf"] += part
            except socket.timeout: break
        return None

    recv(send("Emulation.setDeviceMetricsOverride",
              {"width": 390, "height": 844, "deviceScaleFactor": 2, "mobile": True}))
    send("Emulation.setUserAgentOverride", {"userAgent": UA})
    send("Page.enable"); send("Runtime.enable")
    send("Page.navigate", {"url": URL})
    for _ in range(20):
        time.sleep(1.2)
        d = recv(send("Runtime.evaluate", {"expression": "!!document.querySelector('#view .card')",
                                           "returnByValue": True}), timeout=30)
        if (d or {}).get("result", {}).get("result", {}).get("value"): break
    d = recv(send("Runtime.evaluate", {"expression": """JSON.stringify({
      theme: document.documentElement.dataset.theme,
      overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      titleColor: getComputedStyle(document.querySelector('#title')).color,
      bodyBg: getComputedStyle(document.body).backgroundColor,
      cardBg: (function(){var c=document.querySelector('#view .card');return c?getComputedStyle(c).backgroundColor:'无';})(),
      whale: (function(){var i=document.querySelector('#view img.whale');return i?i.naturalWidth+'x'+i.naturalHeight:'无';})(),
      a2hs: !!document.querySelector('#ios-a2hs') })""", "returnByValue": True}), timeout=30)
    print("测量:", (d or {}).get("result", {}).get("result", {}).get("value"))
    d = recv(send("Page.captureScreenshot", {"format": "png"}), timeout=60)
    pathlib.Path(OUT).write_bytes(base64.b64decode(d["result"]["data"]))
    print("截图:", OUT)
finally:
    proc.kill()
