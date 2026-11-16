import { Card, Hero, MainButton, Mascot, Pill, TextButton } from "@nouride-home/ui-kit/components";
import type { ReactNode } from "react";
import { Page } from "../../commons/components/Page";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import type { Section } from "../../commons/lib/sections";
import { progressWords, versionLines } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";

type Go = (section: Section) => void;

/**
 * One sentence of state, one main button, the cat changing with the state; beside it when there is room, what a
 * person may want to read: where Nouride runs, the versions, a new Nouride, and other devices. `alone` is the first
 * setup, with no sidebar: the state card on its own, in the middle of the window.
 */
export function HomeScreen({ s, go, alone }: { s: Snapshot; go: Go; alone?: boolean }) {
  if (alone) {
    return (
      <div className="flex w-full max-w-[520px] flex-col gap-4">
        <State s={s} address />
      </div>
    );
  }
  return (
    <Page title={t("section_home")}>
      <div className="grid items-start gap-5 @min-[680px]:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-4">
          <State s={s} />
        </div>
        <div className="flex flex-col gap-4">
          {s.nourideUpdate && s.place === "here" && <NourideUpdate s={s} go={go} />}
          <Facts s={s} go={go} />
        </div>
      </div>
    </Page>
  );
}

/** The state card and its button. `address`: the other computer's address under the sentence, where no facts say it. */
function State({ s, address }: { s: Snapshot; address?: boolean }) {
  const { look } = s;
  const button = { start: "action_start", open: "action_open", retry: "action_retry", none: null }[look.action];
  const press = () => {
    if (look.action === "start") ipc.start();
    else if (look.action === "open") ipc.openDashboard();
    else if (look.action === "retry") ipc.retry();
  };
  const p = s.progress && progressWords(s.progress);
  return (
    <>
      <Hero tone={look.tone}>
        <div className="flex flex-col items-center gap-4 text-center">
          <Mascot pose={look.mascot} size={140} />
          <h2 className="font-display text-2xl tracking-tight" data-e2e="state-title">
            {t(`${look.words}_title`)}
          </h2>
          <p className="max-w-[44ch] text-base-content/75">{t(`${look.words}_body`)}</p>
          {address && s.place === "remote" && s.address && <Pill>{s.address}</Pill>}
          {p && (
            <div className="w-full text-left">
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-base-300">
                <div className="bar-moving h-full rounded-full transition-[width] duration-500" style={{ width: `${s.progress?.percent ?? 0}%` }} />
              </div>
              <div className="mt-2 flex justify-between gap-3 text-sm text-base-content/75">
                <span>{p.step}</span>
                <span className="shrink-0">
                  {p.count} · {p.percent}
                </span>
              </div>
            </div>
          )}
        </div>
      </Hero>
      {button && <MainButton onClick={press}>{t(button)}</MainButton>}
    </>
  );
}

/** "A new Nouride is ready": Update opens Versions and updates and starts it there. */
function NourideUpdate({ s, go }: { s: Snapshot; go: Go }) {
  return (
    <Card tone="place">
      <div className="flex items-center gap-4">
        <Mascot pose="update" size={60} />
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{t("nouride_update_title")}</p>
          <p className="text-sm text-base-content/75">{t("nouride_update_body", { version: s.nourideUpdate })}</p>
        </div>
      </div>
      <div className="mt-3 flex justify-end">
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
  );
}

function Fact({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-3 text-[15px] first:pt-0 last:pb-0">
      <p className="mb-0.5 text-xs font-bold uppercase tracking-wide text-base-content/55">{title}</p>
      {children}
    </div>
  );
}

/** What a person may read here: never a port, a path or a process state. */
function Facts({ s, go }: { s: Snapshot; go: Go }) {
  const others =
    s.place === "remote"
      ? "facts_others_remote"
      : s.adopted
        ? "facts_others_adopted"
        : s.openToOthers
          ? "facts_others_on"
          : "this_computer_title";
  return (
    <Card>
      <div className="flex flex-col divide-y divide-base-300">
        <Fact title={t("place_title")}>
          {s.place === "remote" && s.address ? (
            <span>
              <Pill>{s.address}</Pill>
            </span>
          ) : (
            <p>{t("place_here")}</p>
          )}
        </Fact>
        <Fact title={t("facts_versions")}>
          {versionLines(s).map((line) => (
            <p key={line}>{line}</p>
          ))}
          {/* Nouride's own update has its card above: this line is for the app's. */}
          {s.versionsRow && s.appUpdate && (
            <div className="-ml-3 flex flex-wrap items-center gap-1">
              <TextButton onClick={() => go("versions")}>{t(s.versionsRow)}</TextButton>
            </div>
          )}
        </Fact>
        <Fact title={t("access_title")}>
          <p className="text-base-content/80">{t(others)}</p>
          <div className="-ml-3">
            <TextButton onClick={() => go("connect")}>{t("go_connect")}</TextButton>
          </div>
        </Fact>
      </div>
    </Card>
  );
}
