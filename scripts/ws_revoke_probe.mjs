// WS 撤销断开探针：连接事件流 → 等 hello → （外部撤销设备）→ 应收到 CLOSED
// 用法：node ws_revoke_probe.mjs <token> [base]
const token = process.argv[2];
const base = process.argv[3] || 'ws://127.0.0.1:17731';
const ws = new WebSocket(`${base}/mobile/events?token=${token}`);
let gotHello = false;
const t0 = Date.now();
ws.onmessage = (e) => {
  const s = String(e.data);
  if (s.includes('"hello"') && !gotHello) { gotHello = true; console.log(`[${Date.now()-t0}ms] HELLO`); }
};
ws.onclose = (e) => { console.log(`[${Date.now()-t0}ms] CLOSED code=${e.code}`); process.exit(0); };
ws.onerror = () => {};
setTimeout(() => { console.log('TIMEOUT 未被断开'); process.exit(2); }, 20000);
