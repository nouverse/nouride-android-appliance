import { Card, Hero, MainButton, Mascot, Pill, TabHeader } from "@nouride-home/ui-kit/components";
import { ipc, type Screen, type Snapshot } from "../../commons/lib/ipc";
import { progressWords } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";

/** One sentence of state, one main button, the cat changing with the state. */
export function HomeTab({ s, go }: { s: Snapshot; go: (screen: Screen) => void }) {
  const { look } = s;
  const button = { start: "action_start", open: "action_open", retry: "action_retry", none: null }[look.action];
  const press = () => {
    if (look.action === "start") ipc.start();
    else if (look.action === "open") ipc.openDashboard();
    else if (look.action === "retry") ipc.retry();
  };
  const p = s.progress && progressWords(s.progress);

  return (
    <div className="flex flex-col gap-4">
      <TabHeader title={t("tab_home")} dev={s.dev} />
      {s.nourideUpdate && s.place === "here" && (
        <Card tone="place">
          <div className="flex items-center gap-4">
            <Mascot pose="update" size={64} />
            <div className="flex-1">
              <p className="font-semibold">{t("nouride_update_title")}</p>
              <p className="text-sm text-base-content/75">{t("nouride_update_body", { version: s.nourideUpdate })}</p>
            </div>
            <button
              type="button"
              className="btn btn-sm btn-secondary"
              onClick={() => {
                go("versions");
                ipc.updateNouride();
              }}
            >
              {t("update_button")}
            </button>
          </div>
        </Card>
      )}
      <Hero tone={look.tone}>
        <div className="flex flex-col items-center gap-4 text-center">
          <Mascot pose={look.mascot} size={150} />
          <h2 className="font-display text-2xl tracking-tight" data-e2e="state-title">
            {t(`${look.words}_title`)}
          </h2>
          <p className="text-base-content/75">{t(`${look.words}_body`)}</p>
          {s.place === "remote" && s.address && <Pill>{s.address}</Pill>}
          {p && (
            <div className="w-full text-left">
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-base-300">
                <div className="bar-moving h-full rounded-full transition-[width] duration-500" style={{ width: `${s.progress?.percent ?? 0}%` }} />
              </div>
              <div className="mt-2 flex justify-between text-sm text-base-content/75">
                <span>{p.step}</span>
                <span>
                  {p.count} · {p.percent}
                </span>
              </div>
            </div>
          )}
        </div>
      </Hero>
      {button && <MainButton onClick={press}>{t(button)}</MainButton>}
    </div>
  );
}
