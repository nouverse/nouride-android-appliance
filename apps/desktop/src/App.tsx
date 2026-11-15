import { useEffect, useState } from "react";
import { TabBar } from "./commons/components/TabBar";
import { useSnapshot } from "./commons/hooks/useSnapshot";
import { ipc, type Screen } from "./commons/lib/ipc";
import { back, tabOf } from "./commons/lib/screens";
import { ConnectTab } from "./modules/connect/ConnectTab";
import { WhereScreen } from "./modules/first-run/WhereScreen";
import { HomeTab } from "./modules/home/HomeTab";
import { LogsScreen } from "./modules/logs/LogsScreen";
import { MoreTab } from "./modules/more/MoreTab";
import { VersionsScreen } from "./modules/versions/VersionsScreen";

export function App() {
  const s = useSnapshot();
  const [screen, setScreen] = useState<Screen>("home");

  useEffect(() => {
    const stop = ipc.onScreen(setScreen);
    return () => {
      stop.then((f) => f());
    };
  }, []);

  // The keyboard's Back (Cmd+[ or Escape) goes where the bar's Back would.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" || (e.metaKey && e.key === "[")) {
        const to = back(screen);
        if (to) setScreen(to);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [screen]);

  if (!s) return null;
  if (s.place === null) return <WhereScreen s={s} />;

  const page = (() => {
    switch (screen) {
      case "home":
        return <HomeTab s={s} go={setScreen} />;
      case "connect":
        return <ConnectTab s={s} go={setScreen} />;
      case "more":
        return <MoreTab s={s} go={setScreen} />;
      case "versions":
        return <VersionsScreen s={s} go={setScreen} />;
      case "logs":
        return <LogsScreen s={s} go={setScreen} />;
    }
  })();

  return (
    <div className="flex h-full flex-col">
      <main key={screen} className="flex-1 overflow-y-auto px-5 pt-6 pb-4 animate-fade">
        {page}
      </main>
      {s.barShown && screen !== "logs" && <TabBar open={tabOf(screen)} onOpen={setScreen} moreDot={s.moreDot} />}
    </div>
  );
}
