import { Card } from "@nouride-home/ui-kit/components";
import { useEffect } from "react";
import { Page } from "../../commons/components/Page";
import { ipc, type Snapshot, type VersionCard } from "../../commons/lib/ipc";
import { cardWords } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";

function VersionCardView({ name, card, onPress }: { name: string; card: VersionCard; onPress: () => void }) {
  const { words, label } = cardWords(card);
  return (
    <Card>
      <p className="font-display text-xl">{name}</p>
      {card.version && <p className="text-sm text-base-content/65">{t("app_version", { version: card.version })}</p>}
      <p className="mt-3">{words}</p>
      {card.progress && (
        <div className="mt-3 h-2.5 w-full overflow-hidden rounded-full bg-base-300">
          <div className="bar-moving h-full rounded-full transition-[width] duration-500" style={{ width: `${card.percent}%` }} />
        </div>
      )}
      {label && (
        <button type="button" className="btn btn-primary btn-sm mt-4" onClick={onPress}>
          {label}
        </button>
      )}
    </Card>
  );
}

/** This app's version and Nouride's, each with a check and its update (Android's Versions and updates). */
export function VersionsScreen({ s }: { s: Snapshot }) {
  useEffect(() => {
    ipc.checkApp();
    if (s.place === "here") ipc.checkNouride();
  }, [s.place]);
  const press = (which: "app" | "nouride", card: VersionCard) => {
    if (card.button === "check") (which === "app" ? ipc.checkApp : ipc.checkNouride)();
    else if (card.button === "update") (which === "app" ? ipc.updateApp : ipc.updateNouride)();
    else if (card.button === "install") ipc.installNouride();
  };
  return (
    <Page title={t("versions_title")}>
      <div className="grid items-start gap-5 @min-[680px]:grid-cols-2">
        <VersionCardView name={s.appName} card={s.versions.app} onPress={() => press("app", s.versions.app)} />
        {s.place === "here" ? (
          <VersionCardView name={t("versions_nouride")} card={s.versions.nouride} onPress={() => press("nouride", s.versions.nouride)} />
        ) : (
          <Card>
            <p className="font-display text-xl">{t("versions_nouride")}</p>
            <p className="mt-3">{t("versions_nouride_remote")}</p>
          </Card>
        )}
      </div>
    </Page>
  );
}
