import { useEffect, useRef, useState } from "react";
import { Page } from "../../commons/components/Page";
import { ipc, type Logs } from "../../commons/lib/ipc";
import { report } from "../../commons/lib/report";
import { t } from "../../commons/lib/words";

/**
 * For whoever helps: the technical state, the app's events and Nouride's log, taking the whole content area and
 * opening at its end. Copy takes it all.
 */
export function LogsScreen() {
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
    <Page
      full
      title={t("support_title")}
      actions={
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
      }
    >
      <pre className="selectable min-h-0 flex-1 overflow-auto rounded-box border border-base-300 bg-base-100/70 p-4 font-mono text-xs leading-relaxed whitespace-pre-wrap">
        {text}
        <div ref={end} />
      </pre>
    </Page>
  );
}
