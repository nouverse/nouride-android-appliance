/** Every word a person reads, from the one file the menu bar reads too. */
import all from "@nouride-home/ui-kit/words.json";

export type WordKey = keyof typeof all;

const words: Record<string, string> = all;

/** The words for `key`, with `{name}` filled from `values`. An unknown key is shown as itself, so a test catches it. */
export function t(key: string, values: Record<string, string | number | null | undefined> = {}): string {
  const text = words[key] ?? key;
  return text.replace(/\{(\w+)\}/g, (whole, name: string) => {
    const value = values[name];
    return value === undefined || value === null ? whole : String(value);
  });
}

export function has(key: string): boolean {
  return typeof words[key] === "string" && words[key] !== "";
}
