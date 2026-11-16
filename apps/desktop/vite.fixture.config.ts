import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// The layout pictures' build: the app's screens with Tauri's API replaced by the fixtures, for a browser on a machine
// with no Tauri. Its own folder, `dist-fixture/`; the app's build (`vite.config.ts`) never sees `fixtures/`.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@tauri-apps/api/core": fileURLToPath(new URL("./fixtures/tauri-core.ts", import.meta.url)),
      "@tauri-apps/api/event": fileURLToPath(new URL("./fixtures/tauri-event.ts", import.meta.url)),
    },
  },
  build: { target: "safari16", outDir: "dist-fixture", emptyOutDir: true },
});
