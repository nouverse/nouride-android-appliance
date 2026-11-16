import type { Snapshot } from "./ipc";
import { t } from "./words";

/** Home's versions, one line each: the app's, and Nouride's while it runs on this computer ("Nouride 0.8.39"). */
export function versionLines(s: Pick<Snapshot, "appVersion" | "nourideVersion" | "place">): string[] {
  const app = t("versions_row_app", { app: s.appVersion });
  return s.nourideVersion && s.place === "here" ? [app, t("versions_row_nouride", { nouride: s.nourideVersion })] : [app];
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
