import { Card, MainButton, Mascot, Pill, TabHeader } from "@nouride-home/ui-kit/components";
import { useEffect, useState } from "react";
import { ipc, type Screen, type Snapshot } from "../../commons/lib/ipc";
import { shown } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";

/** Nouride on another device: this computer's switch and its address, or the other computer's address. */
export function ConnectTab({ s, go }: { s: Snapshot; go: (screen: Screen) => void }) {
  const [restarting, setRestarting] = useState(false);
  return (
    <div className="flex flex-col gap-4">
      <TabHeader title={t("tab_connect")} dev={s.dev} />
      {s.accessSwitch && (
        <Card>
          <label className="flex items-start gap-4">
            <div className="flex-1">
              <p className="font-semibold">{t("access_title")}</p>
              <p className="text-sm text-base-content/75">{t("access_body")}</p>
              {restarting && <p className="mt-2 text-sm text-secondary">{t("access_restarting")}</p>}
            </div>
            <input
              type="checkbox"
              className="toggle toggle-success mt-1"
              checked={s.openToOthers}
              onChange={async (e) => {
                setRestarting(true);
                await ipc.setAccess(e.target.checked);
                setTimeout(() => setRestarting(false), 4000);
              }}
            />
          </label>
        </Card>
      )}
      {s.place === "remote" ? <Address s={s} title="remote_connect_title" body="remote_connect_body" signIn="remote_connect_sign_in" /> : <Here s={s} go={go} />}
    </div>
  );
}

function Here({ s, go }: { s: Snapshot; go: (screen: Screen) => void }) {
  if (s.adopted && s.reach !== "off") {
    return (
      <Card>
        <p className="text-base-content/75">{t("adopted_connect_body")}</p>
      </Card>
    );
  }
  switch (s.reach) {
    case "off":
      return (
        <Card>
          <div className="flex flex-col items-center gap-3 text-center">
            <Mascot pose="hello" size={110} />
            <p className="font-display text-xl">{t("connect_off_title")}</p>
            <p className="text-base-content/75">{t("connect_off_body")}</p>
            <MainButton onClick={() => go("home")}>{t("go_home")}</MainButton>
          </div>
        </Card>
      );
    case "this_computer":
      return (
        <Card>
          <p className="font-semibold">{t("this_computer_title")}</p>
          <p className="text-sm text-base-content/75">{t("this_computer_body")}</p>
        </Card>
      );
    case "no_network":
      return (
        <Card>
          <div className="flex flex-col items-center gap-3 text-center">
            <Mascot pose="unplugged" size={110} />
            <p className="font-display text-xl">{t("connect_no_network_title")}</p>
            <p className="text-base-content/75">{t("connect_no_network_body")}</p>
          </div>
        </Card>
      );
    case "ready":
      return <Address s={s} title="connect_ready_title" body="connect_ready_body" signIn="connect_sign_in_body" />;
  }
}

/** The code a tablet's camera opens (dark on white in both themes, as a camera reads contrast), the address, and Copy. */
function Address({ s, title, body, signIn }: { s: Snapshot; title: string; body: string; signIn: string }) {
  const [svg, setSvg] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const url = s.shareUrl;
  useEffect(() => {
    if (url) ipc.qr(url).then(setSvg);
  }, [url]);
  if (!url) return null;
  return (
    <Card>
      <div className="flex flex-col items-center gap-4 text-center">
        <p className="font-display text-xl">{t(title)}</p>
        <p className="text-sm text-base-content/75">{t(body)}</p>
        {svg && (
          <div
            role="img"
            aria-label={t("connect_qr_description")}
            className="size-48 rounded-box bg-white p-3"
            // The app's own QR code, drawn by Rust from the address: no outside markup.
            dangerouslySetInnerHTML={{ __html: svg }}
          />
        )}
        <Pill large>{shown(url)}</Pill>
        <button
          type="button"
          className="btn btn-outline btn-sm"
          onClick={async () => {
            await ipc.copy(url);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          }}
        >
          {copied ? t("copied") : t("copy")}
        </button>
        <div className="text-left w-full">
          <p className="font-semibold">{t("connect_sign_in_title")}</p>
          <p className="text-sm text-base-content/75">{t(signIn)}</p>
        </div>
      </div>
    </Card>
  );
}
