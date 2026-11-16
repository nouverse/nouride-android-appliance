// Stands in for `@tauri-apps/api/core` in the layout pictures (`vite.fixture.config.ts`): `invoke` answers from a
// fixture named in the address (`?fixture=home-here`), and every action does nothing.
import { QR } from "./qr";
import { LOGS, SNAPSHOTS } from "./snapshots";

const params = new URLSearchParams(location.search);
const snapshot = SNAPSHOTS[params.get("fixture") ?? "home-here"];

export async function invoke<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  switch (command) {
    case "state":
      return snapshot as T;
    case "qr":
      return (QR[String(args?.text)] ?? "") as T;
    case "logs":
      return LOGS as T;
    default:
      return undefined as T;
  }
}
