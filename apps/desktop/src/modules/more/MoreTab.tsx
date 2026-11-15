import { Card, TabHeader, TextButton } from "@nouride-home/ui-kit/components";
import { ChevronRight } from "lucide-react";
import { useState } from "react";
import { Dialog } from "../../commons/components/Dialog";
import { ipc, type Appearance, type Screen, type Snapshot } from "../../commons/lib/ipc";
import { versionsLine } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";
import { AddressCard } from "../first-run/AddressCard";

function Choice<T extends string>({ value, options, onPick }: { value: T; options: { value: T; words: string }[]; onPick: (v: T) => void }) {
  return (
    <div className="grid grid-flow-col auto-cols-fr gap-2">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => onPick(o.value)}
          className={`btn btn-sm rounded-full ${value === o.value ? "bg-secondary/15 text-secondary border-secondary/30" : "btn-ghost border-base-300"}`}
          aria-pressed={value === o.value}
        >
          {t(o.words)}
        </button>
      ))}
    </div>
  );
}

/** What is rarely needed: where Nouride runs, appearance, versions, turning it off, Technical Logs. */
export function MoreTab({ s, go }: { s: Snapshot; go: (screen: Screen) => void }) {
  const [asking, setAsking] = useState(false);
  const [confirmOff, setConfirmOff] = useState(false);
  return (
    <div className="flex flex-col gap-4">
      <TabHeader title={t("tab_more")} dev={s.dev} />
      <Card>
        <p className="mb-3 font-semibold">{t("place_title")}</p>
        <Choice
          value={s.place ?? "here"}
          options={[
            { value: "here", words: "place_here" },
            { value: "remote", words: "place_remote" },
          ]}
          onPick={(v) => (v === "here" ? s.place !== "here" && ipc.chooseHere() : setAsking(true))}
        />
        <p className="mt-3 text-sm text-base-content/75">{t(s.place === "remote" ? "place_remote_body" : "place_here_body")}</p>
        {s.place === "remote" && s.address && (
          <div className="mt-2 flex items-center justify-between gap-2">
            <span className="font-mono text-sm break-all">{s.address}</span>
            <TextButton onClick={() => setAsking(true)}>{t("place_change_address")}</TextButton>
          </div>
        )}
      </Card>
      <Card>
        <p className="mb-3 font-semibold">{t("appearance_title")}</p>
        <Choice<Appearance>
          value={s.appearance}
          options={[
            { value: "system", words: "appearance_system" },
            { value: "light", words: "appearance_light" },
            { value: "dark", words: "appearance_dark" },
          ]}
          onPick={(v) => ipc.setAppearance(v)}
        />
      </Card>
      <button type="button" className="text-left" onClick={() => go("versions")}>
        <Card>
          <div className="flex items-center justify-between gap-2">
            <div>
              <p className="font-semibold">{t("versions_title")}</p>
              <p className={`text-sm ${s.versionsRow ? "text-secondary font-semibold" : "text-base-content/75"}`}>{versionsLine(s)}</p>
            </div>
            <span className="flex items-center gap-2">
              {s.moreDot && <span className="size-2 rounded-full bg-accent" />}
              <ChevronRight className="size-5 text-base-content/50" />
            </span>
          </div>
        </Card>
      </button>
      {s.place === "here" && s.ours && s.canTurnOff && (
        <div className="px-1">
          <TextButton tone="warn" onClick={() => setConfirmOff(true)}>
            {t("turn_off_nouride")}
          </TextButton>
        </div>
      )}
      <div className="px-1">
        <TextButton onClick={() => go("logs")}>{t("details")}</TextButton>
      </div>
      {asking && <AddressCard s={s} onClose={() => setAsking(false)} />}
      {confirmOff && (
        <Dialog onClose={() => setConfirmOff(false)}>
          <h2 className="font-display text-xl tracking-tight">{t("turn_off_title")}</h2>
          <p className="mt-2 text-base-content/75">{t("turn_off_body")}</p>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" className="btn btn-ghost" onClick={() => setConfirmOff(false)}>
              {t("cancel")}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setConfirmOff(false);
                ipc.turnOff();
              }}
            >
              {t("turn_off")}
            </button>
          </div>
        </Dialog>
      )}
    </div>
  );
}
