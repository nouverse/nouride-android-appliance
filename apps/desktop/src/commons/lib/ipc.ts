/**
 * The one way the screens reach Rust: Tauri's `invoke` for what a person does, and the `state` event for what the app
 * is. Nothing else in the screens imports `@tauri-apps/api`. The types mirror `apps/desktop/src-tauri/src/state.rs`;
 * a field is renamed in both or in neither.
 */
import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

export type Place = "here" | "remote";
export type Phase = "stopped" | "installing" | "starting" | "running" | "restarting" | "stopping" | "failed";
export type Mascot = "hello" | "ride" | "thumbs" | "unplugged";
export type Tone = "calm" | "busy" | "good" | "warn";
export type Action = "start" | "open" | "retry" | "none";
export type Reach = "off" | "this_computer" | "no_network" | "ready";
export type Appearance = "system" | "light" | "dark";
export type Step = "download" | "install" | "start";
export type Button = "check" | "update" | "install" | "none";

export interface Look {
  mascot: Mascot;
  tone: Tone;
  /** The key of its sentence: `<words>_title` and `<words>_body`. */
  words: string;
  action: Action;
  busy: boolean;
}

export interface VersionCard {
  /** The version this card is about: the one installed, or the one on offer. */
  version: string | null;
  /** The version on offer, for "Update to {version}". */
  ready: string | null;
  percent: number;
  words: string;
  button: Button;
  label: string | null;
  progress: boolean;
}

export interface Snapshot {
  appName: string;
  appVersion: string;
  dev: boolean;
  /** `null` until the first question is answered. */
  place: Place | null;
  /** The other computer's address as a person reads it (no `https://`), or `null`. */
  address: string | null;
  /** The last address given, to fill the address card. */
  lastAddress: string | null;
  /** Nouride on this computer is the app's own install. */
  ours: boolean;
  /** Nouride on this computer was there before the app. */
  adopted: boolean;
  phase: Phase;
  look: Look;
  /** The first setup is running: the sidebar is hidden and the progress shows. */
  firstStart: boolean;
  progress: { step: Step; number: number; of: number; percent: number } | null;
  sidebarShown: boolean;
  canTurnOff: boolean;
  /** Connect. */
  reach: Reach;
  /** What another device opens: `http://<address>:<port>/dashboard/` here, the other computer's dashboard there. */
  shareUrl: string | null;
  openToOthers: boolean;
  /** Connect's switch is offered: the app's own install, on this computer. */
  accessSwitch: boolean;
  /** Nouride is finishing an update: the switch waits, as a restart now could undo it. */
  accessWaits: boolean;
  /** The Nouride version running, when it answers. */
  nourideVersion: string | null;
  /** A newer Nouride waiting, for Home's card. */
  nourideUpdate: string | null;
  appUpdate: string | null;
  /** The dot on Versions and updates: an update waits there. */
  versionsDot: boolean;
  /** Home's versions line when something waits, or `null` for the versions themselves. */
  versionsRow: string | null;
  appearance: Appearance;
  versions: { app: VersionCard; nouride: VersionCard };
}

/** What Connect on the address card says back: the address taken, or the key of why not. */
export type AddressAnswer = { ok: true; address: string } | { ok: false; error: string };

export interface Logs {
  summary: string;
  parts: { name: string; text: string }[];
}


export const ipc = {
  state: () => invoke<Snapshot>("state"),
  chooseHere: () => invoke<void>("choose_here"),
  /** Reads the address and checks once that something answers there; keeps it only then. */
  chooseRemote: (typed: string) => invoke<AddressAnswer>("choose_remote", { typed }),
  start: () => invoke<void>("start"),
  retry: () => invoke<void>("retry"),
  turnOff: () => invoke<void>("turn_off"),
  openDashboard: () => invoke<void>("open_dashboard"),
  setAccess: (open: boolean) => invoke<void>("set_access", { open }),
  setAppearance: (appearance: Appearance) => invoke<void>("set_appearance", { appearance }),
  checkApp: () => invoke<void>("check_app"),
  updateApp: () => invoke<void>("update_app"),
  checkNouride: () => invoke<void>("check_nouride"),
  updateNouride: () => invoke<void>("update_nouride"),
  installNouride: () => invoke<void>("install_nouride"),
  logs: () => invoke<Logs>("logs"),
  copy: (text: string) => invoke<void>("copy", { text }),
  qr: (text: string) => invoke<string>("qr", { text }),
  onState: (handler: (snapshot: Snapshot) => void): Promise<UnlistenFn> =>
    listen<Snapshot>("state", (event) => handler(event.payload)),
  /** Where the window should go, when the menu bar or an e2e hook asks: a section's name (`sections.ts`). */
  onScreen: (handler: (name: string) => void): Promise<UnlistenFn> => listen<string>("screen", (event) => handler(event.payload)),
};
