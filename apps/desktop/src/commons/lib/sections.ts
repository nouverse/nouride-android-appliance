import type { Snapshot } from "./ipc";

/** The window's sections, siblings in the sidebar: none is "inside" another, so there is no Back between them. */
export type Section = "home" | "connect" | "versions" | "settings" | "logs";

export interface SectionItem {
  section: Section;
  /** The key of its name in `words.json`. */
  words: string;
  /** Set apart at the bottom of the sidebar: Technical Logs, for whoever helps. */
  apart: boolean;
}

/** In the sidebar's order, which is also Cmd+1..5's (Ctrl+1..5 on Linux). */
export const SECTIONS: readonly SectionItem[] = [
  { section: "home", words: "section_home", apart: false },
  { section: "connect", words: "section_connect", apart: false },
  { section: "versions", words: "versions_title", apart: false },
  { section: "settings", words: "settings_title", apart: false },
  { section: "logs", words: "support_title", apart: true },
];

/** Under this window width the sidebar folds to a rail of icons; a narrow window is still a desktop window. */
export const RAIL_BELOW = 720;

export type SidebarShape = "labels" | "rail";

export function sidebarShape(windowWidth: number): SidebarShape {
  return windowWidth < RAIL_BELOW ? "rail" : "labels";
}

/** The sidebar shows once a place is chosen, and not while the first setup runs (`looks::sidebar` says which). */
export function sidebarShown(s: Pick<Snapshot, "place" | "sidebarShown">): boolean {
  return s.place !== null && s.sidebarShown;
}

/** The section that needs the person, for its dot: Versions and updates, while an update waits. */
export function dotted(section: Section, s: Pick<Snapshot, "versionsDot">): boolean {
  return section === "versions" && s.versionsDot;
}

export interface Keys {
  key: string;
  metaKey: boolean;
  ctrlKey: boolean;
  altKey: boolean;
  shiftKey: boolean;
}

/** The section Cmd+1..5 opens (Ctrl on Linux), or `null` for any other key or while the sidebar is hidden. */
export function shortcut(e: Keys, mac: boolean, shown: boolean): Section | null {
  if (!shown || e.altKey || e.shiftKey) return null;
  if (mac ? !e.metaKey || e.ctrlKey : !e.ctrlKey || e.metaKey) return null;
  const n = Number(e.key);
  return Number.isInteger(n) && n >= 1 && n <= SECTIONS.length ? SECTIONS[n - 1].section : null;
}

/** What the menu bar (or an e2e hook) asks the window to show, when it names a section; anything else is ignored. */
export function sectionOf(name: string): Section | null {
  return SECTIONS.some((item) => item.section === name) ? (name as Section) : null;
}

/**
 * The section a new window opens at: the one its address names (`#/versions`, when the menu bar made the window again
 * to show it, `looks::window::main_page`), or Home.
 */
export function startSection(hash: string): Section {
  return sectionOf(hash.replace(/^#\//, "")) ?? "home";
}

/** macOS draws its window buttons over the sidebar (`titleBarStyle: Overlay`); Linux keeps its own title bar. */
export function isMac(userAgent: string): boolean {
  return /Macintosh|Mac OS X/.test(userAgent);
}
