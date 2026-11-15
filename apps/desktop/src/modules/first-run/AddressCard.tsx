import { useState } from "react";
import { Dialog } from "../../commons/components/Dialog";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";

/**
 * "Nouride on another computer": one field, Connect and Cancel. What is wrong is said under the field in its own
 * sentence; Connect checks once that something answers there ("Checking…"), a typo being the likeliest cause.
 */
export function AddressCard({ s, onClose }: { s: Snapshot; onClose: () => void }) {
  const [typed, setTyped] = useState(s.lastAddress ?? "");
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const stopsHere = s.place === "here" && s.ours && s.canTurnOff;

  const connect = async () => {
    setChecking(true);
    setError(null);
    const answer = await ipc.chooseRemote(typed);
    setChecking(false);
    if (answer.ok) onClose();
    else setError(answer.error);
  };

  return (
    <Dialog onClose={onClose}>
      <h2 className="font-display text-xl tracking-tight">{t("where_ask_title")}</h2>
      {stopsHere && <p className="mt-3 rounded-box bg-accent/10 p-3 text-sm text-accent">{t("where_stops_here")}</p>}
      <p className="mt-3 text-base-content/75">{t("where_ask_body")}</p>
      <input
        autoFocus
        type="url"
        inputMode="url"
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        className="input mt-4 w-full font-mono"
        placeholder={t("where_address_hint")}
        value={typed}
        onChange={(e) => setTyped(e.target.value)}
        onKeyDown={(e) => e.key === "Enter" && !checking && connect()}
      />
      {error && <p className="mt-2 text-sm text-accent">{t(error)}</p>}
      <div className="mt-5 flex justify-end gap-2">
        <button type="button" className="btn btn-ghost" onClick={onClose}>
          {t("cancel")}
        </button>
        <button type="button" className="btn btn-primary" onClick={connect} disabled={checking}>
          {checking ? t("where_checking") : t("where_connect")}
        </button>
      </div>
    </Dialog>
  );
}
