// Sends one DevTools command to the Home Dev dashboard WebView (forwarded to 127.0.0.1:29222) and prints its answer.
// Usage: node wv-cdp.mjs <method> '<params json>'
const list = await (await fetch("http://127.0.0.1:29222/json")).json();
const page = list.find((p) => p.type === "page" && p.url.startsWith("http://127.0.0.1:18264/")) ?? list.find((p) => p.type === "page");
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener("open", r, { once: true }));
ws.send(JSON.stringify({ id: 1, method: process.argv[2], params: JSON.parse(process.argv[3] ?? "{}") }));
const msg = await new Promise((r) => ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id === 1) r(m); }));
console.log(JSON.stringify(msg.result ?? msg));
ws.close();
