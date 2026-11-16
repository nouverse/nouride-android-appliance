import { expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SRC = join(import.meta.dir, "../src");

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? sources(path) : /\.tsx?$/.test(name) ? [path] : [];
  });
}

const files = sources(SRC).map((path) => ({ path: relative(SRC, path), text: readFileSync(path, "utf8") }));

// Modals are disruptive: one stands over the window only to confirm something, and the one confirmation is
// Settings' Turn off. A form, a choice or a message opens in the page.
test("the only modal is Settings' Turn off confirmation", () => {
  const uses = files.flatMap((f) => [...f.text.matchAll(/<Dialog\b/g)].map(() => f.path));
  expect(uses).toEqual(["modules/settings/SettingsScreen.tsx"]);
  const settings = files.find((f) => f.path === "modules/settings/SettingsScreen.tsx")!.text;
  expect(settings).toMatch(/\{confirmOff && \(\s*<Dialog/);
});

test("nothing else covers the window", () => {
  const covers = files.filter((f) => f.path !== "commons/components/Dialog.tsx" && /\bfixed inset-0\b|<dialog\b|\bwindow\.(alert|confirm|prompt)\(/.test(f.text));
  expect(covers.map((f) => f.path)).toEqual([]);
});

test("the address for another computer is a card on the first screen and a section in Settings", () => {
  const card = files.find((f) => f.path === "modules/first-run/AddressCard.tsx")!.text;
  expect(card).not.toContain("Dialog");
  expect(files.find((f) => f.path === "modules/first-run/WhereScreen.tsx")!.text).toContain('frame="card"');
  expect(files.find((f) => f.path === "modules/settings/SettingsScreen.tsx")!.text).toContain('frame="section"');
});
