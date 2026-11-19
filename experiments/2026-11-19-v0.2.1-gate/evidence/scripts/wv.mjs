// Evaluates an expression in the Home Dev dashboard WebView on the Note 12 (DevTools forwarded to 127.0.0.1:29222) and
// prints the result as JSON. Usage: node wv.mjs '<expression, may return a promise>'
const list = await (await fetch("http://127.0.0.1:29222/json")).json();
const page = list.find((p) => p.type === "page" && p.url.startsWith("http://127.0.0.1:18264/")) ?? list.find((p) => p.type === "page");
if (!page) { console.log(JSON.stringify({ error: "no page", list: list.map((p) => p.url) })); process.exit(1); }
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener("open", r, { once: true }));
ws.send(JSON.stringify({ id: 1, method: "Runtime.evaluate", params: { expression: process.argv[2], awaitPromise: true, returnByValue: true } }));
const msg = await new Promise((r) => ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id === 1) r(m); }));
console.log(JSON.stringify(msg.result?.result?.value ?? msg.result ?? msg));
ws.close();
