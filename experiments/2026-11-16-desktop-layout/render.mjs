// Draws the layout pictures: the fixture build of apps/ui (`bun run build:fixture`, Tauri's API mocked) served on
// 127.0.0.1 and opened in headless Chromium at a Mac's user agent, so the screens leave room for the window buttons
// as they do on macOS. Run from the repository's root: `node experiments/2026-11-16-desktop-layout/render.mjs`.
// PLAYWRIGHT names Playwright's module (default: the system's /usr/lib/node_modules/playwright), CHROMIUM the browser
// (default: /usr/bin/chromium).
import { createReadStream, existsSync, statSync } from "node:fs";
import { createServer } from "node:http";
import { extname, join, resolve } from "node:path";

const { chromium } = await import(process.env.PLAYWRIGHT ?? "/usr/lib/node_modules/playwright/index.mjs");

const root = resolve("apps/ui/dist-fixture");
const out = resolve("experiments/2026-11-16-desktop-layout");
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".woff2": "font/woff2", ".webp": "image/webp", ".svg": "image/svg+xml" };

const server = createServer((req, res) => {
  const path = join(root, decodeURIComponent(new URL(req.url, "http://x").pathname));
  const file = existsSync(path) && statSync(path).isFile() ? path : join(root, "index.html");
  res.writeHead(200, { "content-type": TYPES[extname(file)] ?? "application/octet-stream" });
  createReadStream(file).pipe(res);
});
await new Promise((ok) => server.listen(0, "127.0.0.1", ok));
const base = `http://127.0.0.1:${server.address().port}/`;

const MAC_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15";
const SIZES = { xl: [1200, 760], default: [980, 640], medium: [820, 560], min: [560, 520] };

// [file, fixture, section, size, theme]
const SHOTS = [
  ["01-first-run", "first-run", null, "default", "light"],
  ["02-setting-up", "setting-up", null, "default", "light"],
  ["03-home-here-update", "home-here", "home", "default", "light"],
  ["04-home-remote", "home-remote", "home", "default", "light"],
  ["05-connect", "home-here", "connect", "default", "light"],
  ["06-settings", "home-here", "settings", "default", "light"],
  ["07-versions", "home-here", "versions", "default", "light"],
  ["08-logs", "home-here", "logs", "default", "light"],
  ["09-connect-remote", "home-remote", "connect", "default", "light"],
  ["10-home", "home-here", "home", "xl", "light"],
  ["11-home", "home-here", "home", "medium", "light"],
  ["12-home", "home-here", "home", "min", "light"],
  ["13-connect", "home-here", "connect", "xl", "light"],
  ["14-connect", "home-here", "connect", "medium", "light"],
  ["15-connect", "home-here", "connect", "min", "light"],
  ["16-home-here-update", "home-here", "home", "default", "dark"],
  ["17-connect", "home-here", "connect", "default", "dark"],
  ["18-settings", "home-here", "settings", "default", "dark"],
  ["19-first-run", "first-run", null, "default", "dark"],
  ["20-home-off", "home-off", "home", "min", "dark"],
  ["21-first-run", "first-run", null, "min", "light"],
  ["22-settings", "home-here", "settings", "min", "light"],
];

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? "/usr/bin/chromium" });
for (const [name, fixture, section, size, theme] of SHOTS) {
  const [width, height] = SIZES[size];
  const context = await browser.newContext({
    viewport: { width, height },
    deviceScaleFactor: 2,
    colorScheme: theme,
    reducedMotion: "reduce",
    userAgent: MAC_UA,
  });
  const page = await context.newPage();
  const query = new URLSearchParams({ fixture, ...(section ? { section } : {}) });
  await page.goto(`${base}?${query}`);
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  const file = `${name}-${width}x${height}-${theme}.png`;
  await page.screenshot({ path: join(out, file) });
  console.log(`${file}${overflow ? "  SIDEWAYS SCROLL" : ""}`);
  await context.close();
}
await browser.close();
server.close();
