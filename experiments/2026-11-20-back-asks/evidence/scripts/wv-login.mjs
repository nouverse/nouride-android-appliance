// Fills the dashboard's sign-in form in the Home Dev WebView (DevTools on 127.0.0.1:29222) and submits it, as typing
// would. The password is read from the file named in argv[3], never printed. Usage: node wv-login.mjs <user> <pass-file>
import { readFileSync } from "node:fs";
const pass = readFileSync(process.argv[3], "utf8").trim();
const list = await (await fetch("http://127.0.0.1:29222/json")).json();
const page = list.find((p) => p.type === "page");
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener("open", r, { once: true }));
const expr = `(() => {
  const set = (el, v) => { Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(el, v); el.dispatchEvent(new Event("input", { bubbles: true })); };
  const user = document.querySelector("input[autocomplete=username], input[name=username], input[type=text]");
  const pw = document.querySelector("input[type=password]");
  if (!user || !pw) return "no form";
  set(user, ${JSON.stringify(process.argv[2])}); set(pw, ${JSON.stringify(pass)});
  (pw.form?.querySelector("button[type=submit]") ?? [...document.querySelectorAll("button")].find((b) => /sign in/i.test(b.textContent)))?.click();
  return "submitted";
})()`;
ws.send(JSON.stringify({ id: 1, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true } }));
const msg = await new Promise((r) => ws.addEventListener("message", (e) => { const m = JSON.parse(e.data); if (m.id === 1) r(m); }));
console.log(JSON.stringify(msg.result?.result?.value ?? msg));
ws.close();
