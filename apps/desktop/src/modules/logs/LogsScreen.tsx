import { ChevronLeft } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { ipc, type Logs, type Screen, type Snapshot } from "../../commons/lib/ipc";
import { report } from "../../commons/lib/report";
import { t } from "../../commons/lib/words";

/** For whoever helps: the technical state, the app's events and Nouride's log, opening at its end. Copy takes it all. */
export function LogsScreen({ go }: { s: Snapshot; go: (screen: Screen) => void }) {
  const [logs, setLogs] = useState<Logs | null>(null);
  const [copied, setCopied] = useState(false);
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ipc.logs().then(setLogs);
  }, []);
  useEffect(() => {
    end.current?.scrollIntoView();
  }, [logs]);
  const text = logs ? report(logs) : "";
  return (
    <div className="flex h-full flex-col gap-3">
      <header className="flex items-center gap-2">
        <button type="button" className="btn btn-ghost btn-circle btn-sm" onClick={() => go("more")} aria-label={t("back")}>
          <ChevronLeft className="size-5" />
        </button>
        <h1 className="flex-1 font-display text-2xl tracking-tight">{t("support_title")}</h1>
        <button
          type="button"
          className="btn btn-outline btn-sm"
          disabled={!logs}
          onClick={async () => {
            await ipc.copy(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          }}
        >
          {copied ? t("copied") : t("copy")}
        </button>
      </header>
      <pre className="selectable min-h-0 flex-1 overflow-auto rounded-box bg-base-300/60 p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap">
        {text}
        <div ref={end} />
      </pre>
    </div>
  );
}
