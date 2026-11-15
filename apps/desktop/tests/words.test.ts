import { describe, expect, test } from "bun:test";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { has, t } from "../src/commons/lib/words";

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? sources(path) : /\.tsx?$/.test(name) ? [path] : [];
  });
}

describe("words", () => {
  test("every key a screen names has its words", () => {
    const missing: string[] = [];
    for (const file of sources(join(import.meta.dir, "../src"))) {
      const text = readFileSync(file, "utf8");
      for (const match of text.matchAll(/\bt\("([a-z_]+)"/g)) if (!has(match[1])) missing.push(`${file}: ${match[1]}`);
      for (const match of text.matchAll(/words: "([a-z_]+)"/g)) if (!has(match[1])) missing.push(`${file}: ${match[1]}`);
    }
    expect(missing).toEqual([]);
  });

  test("placeholders are filled, and a missing value stays visible rather than becoming blank", () => {
    expect(t("nouride_update_body", { version: "0.8.40" })).toBe("Nouride 0.8.40 is ready. Your agents, chats and settings stay as they are.");
    expect(t("versions_downloading", { version: "0.8.40", percent: 42 })).toBe("Downloading 0.8.40… 42%");
    expect(t("versions_downloading", { percent: 42 })).toContain("{version}");
  });

  test("an unknown key is shown as itself, so it is seen", () => {
    expect(t("no_such_key")).toBe("no_such_key");
  });

  test("no word on screen carries jargon a person should never read", () => {
    const all = JSON.parse(readFileSync(join(import.meta.dir, "../../../packages/ui-kit/src/words.json"), "utf8")) as Record<string, string>;
    const banned = /\b(daemon|launchd|launchctl|LaunchAgent|plist|localhost|127\.0\.0\.1|PID|health)\b|!/i;
    const offending = Object.entries(all).filter(([key, text]) => key !== "_note" && banned.test(text));
    expect(offending).toEqual([]);
  });
});
