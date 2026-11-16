import { expect, test } from "bun:test";
import { report } from "../src/commons/lib/report";
import { dotted, isMac, type Keys, SECTIONS, sectionOf, shortcut, sidebarShape, sidebarShown, startSection } from "../src/commons/lib/sections";

const key = (k: string, mods: Partial<Keys> = {}): Keys => ({ key: k, metaKey: false, ctrlKey: false, altKey: false, shiftKey: false, ...mods });

test("the sidebar's sections, in order, with Technical Logs last and set apart", () => {
  expect(SECTIONS.map((s) => s.section)).toEqual(["home", "connect", "versions", "settings", "logs"]);
  expect(SECTIONS.filter((s) => s.apart).map((s) => s.section)).toEqual(["logs"]);
});

test("Cmd+1..5 opens each section on a Mac, Ctrl+1..5 elsewhere", () => {
  expect(shortcut(key("1", { metaKey: true }), true, true)).toBe("home");
  expect(shortcut(key("2", { metaKey: true }), true, true)).toBe("connect");
  expect(shortcut(key("3", { metaKey: true }), true, true)).toBe("versions");
  expect(shortcut(key("4", { metaKey: true }), true, true)).toBe("settings");
  expect(shortcut(key("5", { metaKey: true }), true, true)).toBe("logs");
  expect(shortcut(key("4", { ctrlKey: true }), false, true)).toBe("settings");
});

test("other keys, the other platform's modifier, and extra modifiers open nothing", () => {
  expect(shortcut(key("6", { metaKey: true }), true, true)).toBeNull();
  expect(shortcut(key("0", { metaKey: true }), true, true)).toBeNull();
  expect(shortcut(key("1"), true, true)).toBeNull();
  expect(shortcut(key("1", { ctrlKey: true }), true, true)).toBeNull();
  expect(shortcut(key("1", { metaKey: true }), false, true)).toBeNull();
  expect(shortcut(key("1", { metaKey: true, shiftKey: true }), true, true)).toBeNull();
  expect(shortcut(key("1", { metaKey: true, altKey: true }), true, true)).toBeNull();
  expect(shortcut(key("a", { metaKey: true }), true, true)).toBeNull();
});

test("no shortcut opens a section while the sidebar is hidden", () => {
  expect(shortcut(key("2", { metaKey: true }), true, false)).toBeNull();
});

test("the dot sits on Versions and updates while an update waits, nowhere else", () => {
  expect(dotted("versions", { versionsDot: true })).toBe(true);
  expect(dotted("versions", { versionsDot: false })).toBe(false);
  for (const section of ["home", "connect", "settings", "logs"] as const) expect(dotted(section, { versionsDot: true })).toBe(false);
});

test("no sidebar before a place is chosen, nor while the first setup runs", () => {
  expect(sidebarShown({ place: null, sidebarShown: true })).toBe(false);
  expect(sidebarShown({ place: "here", sidebarShown: false })).toBe(false);
  expect(sidebarShown({ place: "here", sidebarShown: true })).toBe(true);
  expect(sidebarShown({ place: "remote", sidebarShown: true })).toBe(true);
});

test("the sidebar folds to an icon rail under 720 px, never to a tab bar", () => {
  expect(sidebarShape(560)).toBe("rail");
  expect(sidebarShape(719)).toBe("rail");
  expect(sidebarShape(720)).toBe("labels");
  expect(sidebarShape(820)).toBe("labels");
  expect(sidebarShape(1200)).toBe("labels");
});

test("the menu bar's request names a section, and an old name is ignored", () => {
  expect(sectionOf("versions")).toBe("versions");
  expect(sectionOf("settings")).toBe("settings");
  expect(sectionOf("more")).toBeNull();
});

test("a Mac is told from WebKit's user agent, Linux is not one", () => {
  expect(isMac("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)")).toBe(true);
  expect(isMac("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko)")).toBe(false);
});

test("the copied report has each part under its heading, an empty one said as such", () => {
  expect(report({ summary: "Where: here", parts: [{ name: "Nouride", text: "ready" }, { name: "Installer", text: "  " }] })).toBe(
    "Where: here\n\n== Nouride ==\nready\n\n== Installer ==\n(empty)",
  );
});

test("a window made again opens at the section its address names, or Home", () => {
  expect(startSection("")).toBe("home");
  expect(startSection("#/versions")).toBe("versions");
  expect(startSection("#/settings")).toBe("settings");
  expect(startSection("#/more")).toBe("home");
});
