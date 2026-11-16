import type { BackupLook } from "./ipc";
import { t } from "./words";

/** A size as a Mac's Finder says it: thousands, one decimal below ten ("1.5 MB", "158 MB"). */
export function size(bytes: number): string {
  const units = ["bytes", "KB", "MB", "GB", "TB"];
  let n = bytes;
  let unit = 0;
  while (n >= 1000 && unit < units.length - 1) {
    n /= 1000;
    unit++;
  }
  if (unit === 0) return `${bytes} bytes`;
  return `${n < 10 ? n.toFixed(1).replace(/\.0$/, "") : Math.round(n)} ${units[unit]}`;
}

/** Settings' "Nouride's files" in words: what the zip may weigh, and the line under its button. */
export function backupWords(b: BackupLook): { size: string; result: string | null } {
  const values = { size: b.size === null ? "" : size(b.size), percent: b.percent ?? 0, name: b.name ?? "" };
  return { size: t(b.sizeWords, values), result: b.result ? t(b.result, values) : null };
}
