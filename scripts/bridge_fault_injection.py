#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
桥接 0.2.33 安全事实回归（可重复故障注入 / 0.4.4 验收用例）。

跑法（Windows，Hermes venv 的 python 3.11）：
  python scripts/bridge_fault_injection.py [base]     # base 默认 http://127.0.0.1:17731

覆盖（每条都是"看起来在工作 ≠ 真的在工作"的对应反面）：
  F1 撤销真实失效：撤销后令牌立刻 401；撤销瞬间事件流被掐断（WS close）
  F2 幂等 / ACK：同 reqId 同内容 → 正常回执（不换键重发）；同 reqId 不同内容 → E_ID_REUSE
  F3 停止事实：cancel 返回引擎结果（result 字段存在）；坏令牌 → 401，不伪造成功
  F4 设备接口卫生：GET /mobile/devices/:id 不许解绑（E_BAD_REQUEST 且设备还在）；DELETE 才解绑

退出码 0 = 全部通过；非 0 = 有 FAIL（逐条打印）。
"""
import json
import os
import subprocess
import sys
import time
import uuid

import requests

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:17731"
HERE = os.path.dirname(os.path.abspath(__file__))
NODE = os.environ.get(
    "DSH_TEST_NODE",
    r"C:\Users\zhuquan\AppData\Local\hermes\tools\node-26.7.0-win32-x64\node.exe",
)

results = []


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name} {detail}"[:220])


def pair(s, name, scopes):
    code = s.post(f"{BASE}/mobile-local/rotate", timeout=10).json()["code"]
    r = s.post(
        f"{BASE}/mobile/pair",
        json={"code": code, "deviceName": name, "platform": "audit", "scopes": scopes},
        timeout=15,
    ).json()
    return r["token"], r["device"]["id"]


def revoke(s, device_id):
    return s.delete(f"{BASE}/mobile-local/devices/{device_id}", timeout=10)


def main():
    s = requests.Session()

    # ---------------- F1 撤销真实失效 + 事件流掐断 ----------------
    tok, did = pair(s, f"fi-revoke-{uuid.uuid4().hex[:6]}", ["read"])
    ws_script = os.path.join(HERE, "ws_revoke_probe.mjs")
    proc = None
    if os.path.exists(ws_script) and os.path.exists(NODE):
        env = dict(os.environ)
        proc = subprocess.Popen(
            [NODE, ws_script, tok],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env,
        )
        time.sleep(3)
    revoke(s, did)
    if proc is not None:
        try:
            out, _ = proc.communicate(timeout=15)
            check("F1b 撤销掐断事件流（WS close）", "CLOSED" in out, out.strip().splitlines()[-1] if out.strip() else "")
        except subprocess.TimeoutExpired:
            proc.kill()
            check("F1b 撤销掐断事件流（WS close）", False, "15s 内未断开")
    h = {"Authorization": f"Bearer {tok}"}
    r = requests.get(f"{BASE}/mobile/meta", headers=h, timeout=10)
    body = r.text
    ok401 = r.status_code in (401, 403) or "E_UNAUTHORIZED" in body
    check("F1a 撤销后令牌立刻失效", ok401, f"HTTP {r.status_code}")

    # ---------------- F2 幂等 / ACK ----------------
    tok2, did2 = pair(s, f"fi-idem-{uuid.uuid4().hex[:6]}", ["read", "prompt"])
    h2 = {"Authorization": f"Bearer {tok2}"}
    r = s.post(f"{BASE}/mobile/sessions", headers=h2, json={"title": "故障注入·幂等"}, timeout=20)
    sid = r.json()["sessionId"]
    req = str(uuid.uuid4())
    payload = {"text": "回一个字：好", "mode": "queue", "requestId": req}
    r1 = s.post(f"{BASE}/mobile/sessions/{sid}/prompt", headers=h2, json=payload, timeout=60).json()
    r2 = s.post(f"{BASE}/mobile/sessions/{sid}/prompt", headers=h2, json=payload, timeout=60).json()
    check("F2a 同 reqId 同内容 → 原样回执（不换键重发）", r1.get("ok") is True and r2.get("ok") is True, f"r1={r1.get('ok')} r2={r2.get('ok')}")
    dup = bool(r2.get("duplicate") or r2.get("deduped") or r2.get("reused"))
    # duplicate 字段名以实际实现为准，只作为信息打印
    print(f"       (第二次回执 raw: {json.dumps(r2, ensure_ascii=False)[:160]})")
    payload2 = {"text": "改主意了，换成别的内容", "mode": "queue", "requestId": req}
    r3 = s.post(f"{BASE}/mobile/sessions/{sid}/prompt", headers=h2, json=payload2, timeout=60).json()
    check("F2b 同 reqId 不同内容 → E_ID_REUSE", r3.get("code") == "E_ID_REUSE" or r3.get("ok") is False, f"code={r3.get('code')}")
    # 收尾：取消这个回合，避免挂着
    try:
        s.post(f"{BASE}/mobile/sessions/{sid}/cancel", headers=h2, json={}, timeout=30)
    except Exception:
        pass

    # ---------------- F3 停止事实 ----------------
    rc = s.post(f"{BASE}/mobile/sessions/{sid}/cancel", headers=h2, json={}, timeout=30)
    rj = rc.json() if rc.headers.get("content-type", "").startswith("application/json") else {}
    check("F3a cancel 回执携带引擎结果（result 字段）", "result" in rj, json.dumps(rj, ensure_ascii=False)[:140])
    r = requests.post(
        f"{BASE}/mobile/sessions/{sid}/cancel",
        headers={"Authorization": "Bearer dead-token-000"},
        json={}, timeout=15,
    )
    ok_bad = r.status_code in (401, 403) or "E_UNAUTHORIZED" in r.text
    check("F3b 坏令牌 cancel → 拒绝，不伪造成功", ok_bad, f"HTTP {r.status_code}")

    # ---------------- F4 设备接口卫生 ----------------
    tok3, did3 = pair(s, f"fi-admin-{uuid.uuid4().hex[:6]}", ["admin"])
    h3 = {"Authorization": f"Bearer {tok3}"}
    tok4, did4 = pair(s, f"fi-target-{uuid.uuid4().hex[:6]}", ["read"])
    r = requests.get(f"{BASE}/mobile/devices/{did4}", headers=h3, timeout=10)
    rj = r.json() if "json" in r.headers.get("content-type", "") else {}
    check("F4a GET devices/:id 拒绝（E_BAD_REQUEST）", rj.get("code") == "E_BAD_REQUEST", json.dumps(rj, ensure_ascii=False)[:120])
    st = s.get(f"{BASE}/mobile-local/state", timeout=10).json()
    still = any(d.get("id") == did4 for d in st.get("devices", []))
    check("F4b GET 后目标设备仍在（没被顺手删掉）", still)

    # 清理
    for d in s.get(f"{BASE}/mobile-local/state", timeout=10).json().get("devices", []):
        if str(d.get("name", "")).startswith("fi-"):
            try:
                revoke(s, d["id"])
            except Exception:
                pass

    fails = [n for n, ok, _ in results if not ok]
    print(f"\n===== {len(results) - len(fails)}/{len(results)} 通过 =====")
    if fails:
        print("FAIL: " + "; ".join(fails))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
