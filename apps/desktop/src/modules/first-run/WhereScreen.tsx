import { Card, MainButton, Mascot } from "@nouride-home/ui-kit/components";
import { useState } from "react";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";
import { AddressCard } from "./AddressCard";

/** The first screen on a computer without Nouride: the one thing the app cannot answer for the person. */
export function WhereScreen({ s }: { s: Snapshot }) {
  const [asking, setAsking] = useState(false);
  return (
    <div className="flex h-full flex-col justify-center gap-6 px-6 animate-fade">
      <div className="flex flex-col items-center gap-4 text-center">
        <Mascot pose="hello" size={140} />
        <h1 className="font-display text-3xl tracking-tight">{t("where_title")}</h1>
        <p className="text-base-content/75">{t("where_body")}</p>
      </div>
      <Card>
        <div className="flex flex-col gap-3">
          {/* The press is the person asking for it: the first setup starts at once. */}
          <MainButton onClick={() => ipc.chooseHere()}>{t("where_here")}</MainButton>
          <button type="button" className="btn btn-lg btn-outline w-full font-semibold" onClick={() => setAsking(true)}>
            {t("where_remote")}
          </button>
        </div>
      </Card>
      {asking && <AddressCard s={s} onClose={() => setAsking(false)} />}
    </div>
  );
}
