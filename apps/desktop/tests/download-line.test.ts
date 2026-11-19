import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The script the dashboard's window runs before the page (`downloads::LINE_SCRIPT`), run here against a page made of
// the few things it touches: a body, an element it makes, and a timer.
const source = readFileSync(join(import.meta.dir, "../../../crates/downloads/src/line.js"), "utf8");

function page(withBody = true) {
  const children: FakeElement[] = [];
  const timers: (() => void)[] = [];
  interface FakeElement {
    attrs: Record<string, string>;
    style: { cssText: string };
    textContent: string;
    setAttribute: (k: string, v: string) => void;
    remove: () => void;
  }
  const make = (): FakeElement => {
    const el: FakeElement = {
      attrs: {},
      style: { cssText: "" },
      textContent: "",
      setAttribute: (k, v) => void (el.attrs[k] = v),
      remove: () => void children.splice(children.indexOf(el), 1),
    };
    return el;
  };
  const body = { appendChild: (el: FakeElement) => (children.includes(el) ? el : (children.push(el), el)) };
  const document = { body: withBody ? body : null, createElement: make };
  const window: Record<string, unknown> = {};
  const setTimeout = (f: () => void) => timers.push(f);
  const clearTimeout = () => timers.splice(0, timers.length);
  new Function("document", "window", "setTimeout", "clearTimeout", source)(document, window, setTimeout, clearTimeout);
  const say = (text: string) => (window.__nourideHomeLine as (t: string) => void)(text);
  return { say, children, timers, window };
}

test("the line shows the text as text, announced, and goes after a while", () => {
  const p = page();
  p.say("<b>report.pdf</b> is in Downloads.");
  expect(p.children).toHaveLength(1);
  expect(p.children[0].textContent).toBe("<b>report.pdf</b> is in Downloads.");
  expect(p.children[0].attrs.role).toBe("status");
  p.timers.forEach((f) => f());
  expect(p.children).toHaveLength(0);
});

test("a second line takes the place of the first, with time of its own", () => {
  const p = page();
  p.say("Downloading report.pdf.");
  p.say("report.pdf is in Downloads.");
  expect(p.children).toHaveLength(1);
  expect(p.children[0].textContent).toBe("report.pdf is in Downloads.");
  expect(p.timers).toHaveLength(1);
});

test("before the page has a body, nothing is shown and nothing breaks", () => {
  const p = page(false);
  expect(() => p.say("x")).not.toThrow();
  expect(p.children).toHaveLength(0);
});

test("run twice, it is set once", () => {
  const p = page();
  const first = p.window.__nourideHomeLine;
  new Function("document", "window", source)({ body: null }, p.window);
  expect(p.window.__nourideHomeLine).toBe(first);
});
