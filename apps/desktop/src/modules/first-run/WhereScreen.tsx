import { Mascot } from "@nouride-home/ui-kit/components";
import { Globe, Laptop, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";
import { AddressCard } from "./AddressCard";

function Answer({ icon: Icon, title, body, main, onPress }: { icon: LucideIcon; title: string; body: string; main?: boolean; onPress: () => void }) {
  return (
    <button
      type="button"
      onClick={onPress}
      className={`group flex flex-col items-start gap-3 rounded-box border p-5 text-left shadow-soft transition-colors ${
        main ? "border-primary/30 bg-base-100 hover:border-primary/60" : "border-base-300 bg-base-100 hover:border-secondary/50"
      }`}
    >
      <span className={`grid size-11 place-items-center rounded-full ${main ? "bg-[var(--fill-primary)] text-[var(--fill-ink)]" : "bg-secondary/15 text-secondary"}`}>
        <Icon className="size-5" strokeWidth={2} />
      </span>
      <span className="font-display text-lg leading-snug tracking-tight">{title}</span>
      <span className="text-sm text-base-content/70">{body}</span>
    </button>
  );
}

/**
 * The first screen on a computer without Nouride: the one thing the app cannot answer for the person, in a column in
 * the middle of the window, its two answers side by side. No sidebar until it is answered.
 */
export function WhereScreen({ s, mac }: { s: Snapshot; mac: boolean }) {
  const [asking, setAsking] = useState(false);
  return (
    <main className={`grid h-full place-items-center overflow-y-auto px-7 pb-8 animate-fade ${mac ? "pt-10" : "pt-7"}`}>
      <div className="@container flex w-full max-w-[640px] flex-col gap-6">
        <div className="flex flex-col items-center gap-3 text-center">
          <Mascot pose="hello" size={120} />
          <h1 className="font-display text-3xl tracking-tight">{t("where_title")}</h1>
          <p className="max-w-[48ch] text-base-content/75">{t("where_body")}</p>
        </div>
        {asking ? (
          <AddressCard s={s} onClose={() => setAsking(false)} inline />
        ) : (
          <div className="grid gap-4 @min-[460px]:grid-cols-2">
            {/* The press is the person asking for it: the first setup starts at once. */}
            <Answer icon={Laptop} main title={t("where_here")} body={t("where_here_body")} onPress={() => ipc.chooseHere()} />
            <Answer icon={Globe} title={t("where_remote")} body={t("where_remote_body")} onPress={() => setAsking(true)} />
          </div>
        )}
      </div>
    </main>
  );
}
