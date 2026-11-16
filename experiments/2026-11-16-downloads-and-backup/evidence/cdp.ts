// Usage: bun cdp.ts <port> <js expression>  — evaluates in the first page target, awaits promises, prints the value.
const port = process.argv[2]; const expr = process.argv[3].startsWith("@") ? await Bun.file(process.argv[3].slice(1)).text() : process.argv[3];
const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const ws = new WebSocket(targets.find((t: any) => t.type === "page").webSocketDebuggerUrl);
await new Promise((r) => (ws.onopen = r));
ws.onmessage = (m) => {
  const msg = JSON.parse(String(m.data));
  if (msg.id !== 1) return;
  const r = msg.result?.result;
  console.log(msg.result?.exceptionDetails ? "EXCEPTION " + JSON.stringify(msg.result.exceptionDetails.exception?.description) : typeof r?.value === "string" ? r.value : JSON.stringify(r?.value ?? r));
  ws.close();
  process.exit(0);
};
ws.send(JSON.stringify({ id: 1, method: "Runtime.evaluate", params: { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true } }));
setTimeout(() => { console.log("timeout"); process.exit(1); }, 60000);
