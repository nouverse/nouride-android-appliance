// The states the layout pictures are drawn in: what Rust would send as `state`, made by hand. Never in the app's
// bundle: only `vite.fixture.config.ts` reaches this folder.
import type { Snapshot } from "../src/commons/lib/ipc";

const card = { version: "0.1.0", ready: null, percent: 0, words: "up_to_date", button: "check", label: "check_updates", progress: false } as const;

const running: Snapshot = {
  appName: "Nouride Home",
  appVersion: "0.1.0",
  dev: false,
  place: "here",
  address: null,
  lastAddress: null,
  ours: true,
  adopted: false,
  phase: "running",
  look: { mascot: "thumbs", tone: "good", words: "running", action: "open", busy: false },
  firstStart: false,
  progress: null,
  sidebarShown: true,
  canTurnOff: true,
  reach: "ready",
  shareUrl: "http://192.168.0.20:18264/dashboard/",
  openToOthers: true,
  accessSwitch: true,
  accessWaits: false,
  nourideVersion: "0.8.39",
  nourideUpdate: "0.8.40",
  appUpdate: null,
  versionsDot: true,
  versionsRow: "versions_row_nouride_update",
  appearance: "system",
  versions: {
    app: { ...card },
    nouride: { version: "0.8.39", ready: "0.8.40", percent: 0, words: "versions_nouride_ready", button: "update", label: "versions_update_to", progress: false },
  },
};

export const SNAPSHOTS: Record<string, Snapshot> = {
  "first-run": { ...running, place: null, phase: "stopped", look: { mascot: "hello", tone: "calm", words: "off", action: "start", busy: false } },
  "setting-up": {
    ...running,
    phase: "installing",
    look: { mascot: "ride", tone: "busy", words: "setting_up", action: "none", busy: true },
    firstStart: true,
    progress: { step: "install", number: 2, of: 3, percent: 46 },
    sidebarShown: false,
    reach: "off",
    shareUrl: null,
    nourideVersion: null,
    nourideUpdate: null,
    versionsDot: false,
    versionsRow: null,
  },
  "home-here": running,
  "home-remote": {
    ...running,
    place: "remote",
    address: "nouride.example.com",
    lastAddress: "nouride.example.com",
    ours: false,
    look: { mascot: "thumbs", tone: "good", words: "remote_running", action: "open", busy: false },
    shareUrl: "https://nouride.example.com/dashboard/",
    accessSwitch: false,
    canTurnOff: false,
    nourideVersion: "0.8.39",
    nourideUpdate: null,
    versionsDot: false,
    versionsRow: null,
    versions: { app: { ...card }, nouride: { ...card, version: null, words: "versions_nouride_remote", button: "none", label: null } },
  },
  "home-off": {
    ...running,
    phase: "stopped",
    look: { mascot: "hello", tone: "calm", words: "off", action: "start", busy: false },
    reach: "off",
    shareUrl: null,
    nourideVersion: null,
    nourideUpdate: null,
    versionsDot: false,
    versionsRow: null,
    openToOthers: false,
  },
};

export const LOGS = {
  summary: "Where: this computer\nNouride: running, answering on 127.0.0.1:18264\nInstalled by the app: yes\nOpen to other devices: yes",
  parts: [
    {
      name: "Nouride Home",
      text: [
        "1794773677 Nouride Home 0.1.0 started",
        "1794773680 place: this computer",
        "1794773680 first setup: start",
        "1794773723 first setup: Nouride answers",
        "1794773726 nouride update check: ready Some(\"0.8.40\")",
        "1794773740 dashboard sign-in: session handed to the window",
        "1794773804 open to other devices: true (Ok(true))",
        "1794791746 a link outside Nouride went to the browser: accounts.google.com",
      ].join("\n"),
    },
    { name: "Installer", text: "Downloading nouride v0.8.39 (router, darwin-arm64)…\nVerified the checksum.\nInstalled into the app's folder." },
    {
      name: "Nouride",
      text: Array.from({ length: 40 }, (_, i) => `2026-11-16T01:${String(10 + Math.floor(i / 2)).padStart(2, "0")}:0${i % 10}Z info  gateway telegram: polling (${i})`).join("\n"),
    },
  ],
};
