import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { join } from "node:path";

// The script the dashboard's window runs before the page (`link_policy::NEW_TABS_SCRIPT`), run here against a page
// made of the few things it touches: a click listener on the document, anchors, and `window.open`.
const source = readFileSync(join(import.meta.dir, "../../../crates/link-policy/src/new-tabs.js"), "utf8");

interface FakeNode {
  nodeName: string;
  parentNode: FakeNode | null;
  href?: string;
  attrs?: Record<string, string>;
  getAttribute?: (name: string) => string | null;
  hasAttribute?: (name: string) => boolean;
}

function page() {
  let listener: ((event: unknown) => void) | null = null;
  let capture = false;
  const opened: unknown[][] = [];
  const document = {
    addEventListener: (type: string, f: (event: unknown) => void, useCapture: boolean) => {
      if (type === "click") {
        listener = f;
        capture = useCapture;
      }
    },
  };
  const window = { open: (...args: unknown[]) => opened.push(args) };
  new Function("document", "window", source)(document, window);
  const click = (target: FakeNode, init: { button?: number; defaultPrevented?: boolean } = {}) => {
    const event = { target, button: init.button ?? 0, defaultPrevented: init.defaultPrevented ?? false, preventDefault() {
      this.defaultPrevented = true;
    } };
    listener?.(event);
    return event.defaultPrevented;
  };
  return { click, opened, capture: () => capture };
}

function anchor(href: string, attrs: Record<string, string> = {}): FakeNode {
  return {
    nodeName: "A",
    parentNode: null,
    href,
    attrs,
    getAttribute: (name) => attrs[name] ?? null,
    hasAttribute: (name) => name in attrs,
  };
}

test("Sign In with Google, a new-tab link, is opened with window.open, where the app's handler sees it", () => {
  const p = page();
  const google = anchor("http://127.0.0.1:18264/llm-gateway/oauth/start", { target: "_blank", rel: "noreferrer noopener" });
  expect(p.click(google)).toBe(true);
  expect(p.opened).toEqual([["http://127.0.0.1:18264/llm-gateway/oauth/start", "_blank", "noopener"]]);
  expect(p.capture()).toBe(true);
});

test("a click on the icon inside the button counts as the link's", () => {
  const p = page();
  const link = anchor("https://auth.openai.com/oauth/authorize?x=1", { target: "_blank" });
  const icon: FakeNode = { nodeName: "svg", parentNode: link };
  expect(p.click(icon)).toBe(true);
  expect(p.opened[0][0]).toBe("https://auth.openai.com/oauth/authorize?x=1");
});

test("ordinary links, downloads, other buttons and handled clicks are left to the page", () => {
  const p = page();
  expect(p.click(anchor("http://127.0.0.1:18264/dashboard/agents"))).toBe(false);
  expect(p.click(anchor("http://127.0.0.1:18264/dashboard/agents", { target: "_self" }))).toBe(false);
  expect(p.click(anchor("http://127.0.0.1:18264/x.zip", { target: "_blank", download: "" }))).toBe(false);
  expect(p.click(anchor("mailto:someone@example.com", { target: "_blank" }))).toBe(false);
  expect(p.click(anchor("https://github.com/login/device", { target: "_blank" }), { button: 1 })).toBe(false);
  expect(p.click(anchor("https://github.com/login/device", { target: "_blank" }), { defaultPrevented: true })).toBe(true);
  expect(p.click({ nodeName: "BUTTON", parentNode: null })).toBe(false);
  expect(p.opened).toEqual([]);
});
