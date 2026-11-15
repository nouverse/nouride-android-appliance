import { describe, expect, test } from "bun:test";
import type { VersionCard } from "../src/commons/lib/ipc";
import { cardWords, progressWords, shown, versionsLine } from "../src/commons/lib/versions";

const card = (over: Partial<VersionCard>): VersionCard => ({
  version: "0.8.39",
  ready: null,
  percent: 0,
  words: "up_to_date",
  button: "check",
  label: "check_updates",
  progress: false,
  ...over,
});

describe("More's Versions line", () => {
  test("says what waits, before the versions", () => {
    expect(versionsLine({ versionsRow: "versions_row_nouride_update", appVersion: "0.1.0", nourideVersion: "0.8.39", place: "here" })).toBe(
      "A new version of Nouride is ready.",
    );
  });
  test("names both versions while Nouride runs here, and the app's alone otherwise", () => {
    expect(versionsLine({ versionsRow: null, appVersion: "0.1.0", nourideVersion: "0.8.39", place: "here" })).toBe("Nouride Home 0.1.0, Nouride 0.8.39");
    expect(versionsLine({ versionsRow: null, appVersion: "0.1.0", nourideVersion: "0.8.39", place: "remote" })).toBe("Nouride Home 0.1.0");
    expect(versionsLine({ versionsRow: null, appVersion: "0.1.0", nourideVersion: null, place: "here" })).toBe("Nouride Home 0.1.0");
  });
});

describe("a version card", () => {
  test("offers the version on offer, not the one installed", () => {
    const words = cardWords(card({ ready: "0.8.40", words: "versions_nouride_ready", button: "update", label: "versions_update_to" }));
    expect(words).toEqual({ words: "Version 0.8.40 is ready. Your agents, chats and settings stay as they are.", label: "Update to 0.8.40" });
  });
  test("a download says its percent, with no button", () => {
    const words = cardWords(card({ ready: "0.8.40", percent: 42, words: "versions_downloading", button: "none", label: null, progress: true }));
    expect(words).toEqual({ words: "Downloading 0.8.40… 42%", label: null });
  });
});

describe("the first setup's bar", () => {
  test("says the step in words, its number and the percent", () => {
    expect(progressWords({ step: "install", number: 2, of: 3, percent: 57 })).toEqual({
      step: "Installing Nouride",
      count: "Step 2 of 3",
      percent: "57%",
    });
  });
});

test("an address is shown without https:// or the dashboard's path", () => {
  expect(shown("https://nouride.example.com/dashboard/")).toBe("nouride.example.com");
  expect(shown("http://192.168.1.20:18254/dashboard/")).toBe("http://192.168.1.20:18254");
});
