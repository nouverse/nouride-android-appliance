import { useEffect, useState } from "react";
import { Sidebar } from "./commons/components/Sidebar";
import { useSnapshot } from "./commons/hooks/useSnapshot";
import { useWindowWidth } from "./commons/hooks/useWindowWidth";
import { ipc } from "./commons/lib/ipc";
import { isMac, type Section, sectionOf, shortcut, sidebarShape, sidebarShown, startSection } from "./commons/lib/sections";
import { ConnectScreen } from "./modules/connect/ConnectScreen";
import { WhereScreen } from "./modules/first-run/WhereScreen";
import { HomeScreen } from "./modules/home/HomeScreen";
import { LogsScreen } from "./modules/logs/LogsScreen";
import { SettingsScreen } from "./modules/settings/SettingsScreen";
import { VersionsScreen } from "./modules/versions/VersionsScreen";

const MAC = isMac(navigator.userAgent);

export function App() {
  const s = useSnapshot();
  const [section, setSection] = useState<Section>(() => startSection(location.hash));
  const width = useWindowWidth();
  const shown = s ? sidebarShown(s) : false;

  useEffect(() => {
    const stop = ipc.onScreen((name) => {
      const to = sectionOf(name);
      if (to) setSection(to);
    });
    return () => {
      stop.then((f) => f());
    };
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const to = shortcut(e, MAC, shown);
      if (to) {
        e.preventDefault();
        setSection(to);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [shown]);

  if (!s) return null;

  // macOS draws its window buttons over the window's top (`titleBarStyle: Overlay`): that strip moves the window.
  const titleStrip = MAC && <div data-tauri-drag-region className="fixed inset-x-0 top-0 z-10 h-8" />;

  if (s.place === null) {
    return (
      <>
        {titleStrip}
        <WhereScreen s={s} mac={MAC} />
      </>
    );
  }

  // The first setup: no sidebar, its progress the one thing to watch.
  if (!shown) {
    return (
      <>
        {titleStrip}
        <main className={`grid h-full place-items-center overflow-y-auto px-7 pb-8 animate-fade ${MAC ? "pt-10" : "pt-7"}`}>
          <HomeScreen s={s} go={setSection} alone />
        </main>
      </>
    );
  }

  const page = (() => {
    switch (section) {
      case "home":
        return <HomeScreen s={s} go={setSection} />;
      case "connect":
        return <ConnectScreen s={s} go={setSection} />;
      case "versions":
        return <VersionsScreen s={s} />;
      case "settings":
        return <SettingsScreen s={s} />;
      case "logs":
        return <LogsScreen />;
    }
  })();

  return (
    <div className="flex h-full">
      {titleStrip}
      <Sidebar s={s} open={section} onOpen={setSection} shape={sidebarShape(width)} mac={MAC} />
      <main key={section} className={`min-w-0 flex-1 overflow-y-auto px-7 pb-8 animate-fade ${MAC ? "pt-10" : "pt-7"}`}>
        {page}
      </main>
    </div>
  );
}
