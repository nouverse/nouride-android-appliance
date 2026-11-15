import type { Screen } from "./ipc";

export type Tab = "home" | "connect" | "more";

/** Where Back goes: from Connect or More to Home; from Versions or Technical Logs to More; from Home nowhere. */
export function back(from: Screen): Screen | null {
  if (from === "home") return null;
  if (from === "versions" || from === "logs") return "more";
  return "home";
}

/** The tab a screen belongs to, for the bar's pill. */
export function tabOf(screen: Screen): Tab {
  return screen === "versions" || screen === "logs" ? "more" : screen;
}
