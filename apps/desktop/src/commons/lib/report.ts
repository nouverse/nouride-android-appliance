import type { Logs } from "./ipc";

/** The report as it is copied: the summary, then each part under its own heading. Android's `SupportText.report`. */
export function report(logs: Logs): string {
  let out = logs.summary;
  for (const part of logs.parts) {
    out += `\n\n== ${part.name} ==\n${part.text.trim() === "" ? "(empty)" : part.text}`;
  }
  return out;
}
