import { useEffect, useRef, useState } from "react";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";

/**
 * "Nouride on another computer": one field, Connect and Cancel. A card of its own in the first question's column; in
 * Settings a section of the place's card, under the choice. Never over the window: a modal is only for a confirmation.
 * What is wrong is said under the field in its own sentence; Connect checks once that something answers there
 * ("Checking…"), a typo being the likeliest cause.
 */
export function AddressCard({ s, onClose, frame }: { s: Snapshot; onClose: () => void; frame: "card" | "section" }) {
  const [typed, setTyped] = useState(s.lastAddress ?? "");
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const stopsHere = s.place === "here" && s.ours && s.canTurnOff;
  const section = useRef<HTMLElement>(null);
  // In a short window the section opens partly below the fold: Connect is brought into view with it.
  useEffect(() => section.current?.scrollIntoView({ block: "nearest" }), []);

  const connect = async () => {
    setChecking(true);
    setError(null);
    const answer = await ipc.chooseRemote(typed);
    setChecking(false);
    if (answer.ok) onClose();
    else setError(answer.error);
  };

  const card = (
    <>
      {frame === "card" ? (
        <h2 className="font-display text-xl tracking-tight">{t("where_ask_title")}</h2>
      ) : (
        <h3 className="font-display text-lg tracking-tight">{t("where_ask_title")}</h3>
      )}
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
    </>
  );
  // On the first screen it takes the answers' place; in Settings it opens under the choice, inside the same card.
  return frame === "card" ? (
    <div className="rounded-box border border-base-300 bg-base-100 p-6 shadow-soft">{card}</div>
  ) : (
    <section ref={section} aria-label={t("where_ask_title")} className="mt-4 border-t border-base-300 pt-4 animate-fade">
      {card}
    </section>
  );
}
