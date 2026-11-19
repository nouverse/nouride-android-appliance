import { Card, TextButton } from "@nouride-home/ui-kit/components";
import { type ReactNode, useEffect, useState } from "react";
import { backupWords } from "../../commons/lib/backup";
import { Dialog } from "../../commons/components/Dialog";
import { ipc, type Appearance, type Snapshot } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";
import { AddressCard } from "../first-run/AddressCard";
import { pressPlace, shownPlace } from "./placeChoice";

function Choice<T extends string>({ value, options, onPick }: { value: T; options: { value: T; words: string }[]; onPick: (v: T) => void }) {
  return (
    <div className="inline-flex flex-wrap gap-1 rounded-full bg-base-200 p-1" role="radiogroup">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          onClick={() => onPick(o.value)}
          className={`btn btn-sm rounded-full border-0 px-4 shadow-none ${value === o.value ? "bg-secondary/15 text-secondary" : "btn-ghost text-base-content/75"}`}
          aria-checked={value === o.value}
        >
          {t(o.words)}
        </button>
      ))}
    </div>
  );
}

/** A group of the settings page: its name above a card, as a computer's settings window has them. */
function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="px-1 text-sm font-bold text-base-content/65">{title}</h2>
      <Card>{children}</Card>
    </section>
  );
}

/** Where Nouride runs, how the app looks, and turning Nouride off, on one page. */
export function SettingsScreen({ s }: { s: Snapshot }) {
  const [asking, setAsking] = useState(false);
  const [confirmOff, setConfirmOff] = useState(false);
  // What "Download Nouride's files" would weigh, counted each time Settings is shown.
  useEffect(() => {
    if (s.backup.shown) ipc.backupCount();
  }, [s.backup.shown]);
  const backup = backupWords(s.backup);
  return (
    <div className="@container mx-auto flex w-full max-w-[720px] flex-col gap-6">
      <header className="flex min-h-10 items-center">
        <h1 className="font-display text-[26px] leading-tight tracking-tight">{t("settings_title")}</h1>
      </header>
      <Group title={t("place_title")}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="min-w-0 flex-1 basis-56 text-base-content/80">{t(s.place === "remote" ? "place_remote_body" : "place_here_body")}</p>
          <Choice
            value={shownPlace(s.place, asking)}
            options={[
              { value: "here", words: "place_here" },
              { value: "remote", words: "place_remote" },
            ]}
            onPick={(v) => {
              const press = pressPlace(s.place, v);
              setAsking(press.asking);
              if (press.chooseHere) ipc.chooseHere();
            }}
          />
        </div>
        {asking && <AddressCard s={s} onClose={() => setAsking(false)} frame="section" />}
        {!asking && s.place === "remote" && s.address && (
          <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-base-300 pt-4">
            <span className="min-w-0 font-mono text-sm break-all">{s.address}</span>
            <TextButton onClick={() => setAsking(true)}>{t("place_change_address")}</TextButton>
          </div>
        )}
      </Group>
      <Group title={t("appearance_title")}>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="min-w-0 flex-1 basis-56 text-base-content/80">{t("appearance_body")}</p>
          <Choice<Appearance>
          value={s.appearance}
          options={[
            { value: "system", words: "appearance_system" },
            { value: "light", words: "appearance_light" },
            { value: "dark", words: "appearance_dark" },
          ]}
          onPick={(v) => ipc.setAppearance(v)}
          />
        </div>
      </Group>
      {s.backup.shown && (
        <Group title={t("backup_title")}>
          <div className="flex flex-col gap-3">
            <p className="text-base-content/80">{t("backup_body")}</p>
            <p className="text-base-content/80">{t(s.backup.secrets)}</p>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="min-w-0 flex-1 basis-56 text-base-content/65">{backup.size}</p>
              <button type="button" className="btn btn-primary btn-sm" disabled={!s.backup.button} onClick={() => ipc.backupStart()}>
                {t("backup_button")}
              </button>
            </div>
            {s.backup.bar && (
              <progress className="progress progress-success w-full" value={s.backup.percent ?? undefined} max={100} aria-label={backup.result ?? undefined} />
            )}
            {backup.result && (
              <p role="status" className="text-base-content">
                {backup.result}
              </p>
            )}
          </div>
        </Group>
      )}
      {s.place === "here" && s.ours && s.canTurnOff && (
        <Group title={t("settings_nouride_title")}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="min-w-0 flex-1 basis-56 text-base-content/80">{t("settings_nouride_body")}</p>
            <TextButton tone="warn" onClick={() => setConfirmOff(true)}>
              {t("turn_off_nouride")}
            </TextButton>
          </div>
        </Group>
      )}
      {/* The one modal: a confirmation. Everything else opens in the page. */}
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
