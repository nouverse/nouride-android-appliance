import type { Snapshot } from "./ipc";
import { t } from "./words";

/** More's Versions line: what waits there, or the two versions in one line ("Nouride Home 0.1.0, Nouride 0.8.39"). */
export function versionsLine(s: Pick<Snapshot, "versionsRow" | "appVersion" | "nourideVersion" | "place">): string {
  if (s.versionsRow) return t(s.versionsRow);
  if (s.nourideVersion && s.place === "here") return t("versions_row_both", { app: s.appVersion, nouride: s.nourideVersion });
  return t("versions_row_app", { app: s.appVersion });
}

/** A card's sentence and button label, filled with its version and percent. */
export function cardWords(card: Snapshot["versions"]["app"]): { words: string; label: string | null } {
  const values = { version: card.ready ?? card.version, percent: card.percent };
  return { words: t(card.words, values), label: card.label ? t(card.label, values) : null };
}

/** "Step 2 of 3" and "45%" for the first setup's bar. */
export function progressWords(p: NonNullable<Snapshot["progress"]>): { step: string; count: string; percent: string } {
  return {
    step: t(`step_${p.step}`),
    count: t("step_count", { step: p.number, steps: p.of }),
    percent: t("percent", { percent: p.percent }),
  };
}

/** An address as a person reads it: without `https://`. */
export function shown(url: string): string {
  return url.replace(/^https:\/\//, "").replace(/\/dashboard\/?$/, "");
}
