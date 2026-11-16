import { Card, MainButton, Mascot, Pill } from "@nouride-home/ui-kit/components";
import { useEffect, useState } from "react";
import { Page } from "../../commons/components/Page";
import { ipc, type Snapshot } from "../../commons/lib/ipc";
import type { Section } from "../../commons/lib/sections";
import { shown } from "../../commons/lib/versions";
import { t } from "../../commons/lib/words";

/**
 * Nouride on another device: this computer's switch and its address, or the other computer's address. With room, the
 * code stands beside the address and the switch; in a narrower window they stack.
 */
export function ConnectScreen({ s, go }: { s: Snapshot; go: (section: Section) => void }) {
  const remote = s.place === "remote";
  const ready = remote || (!(s.adopted && s.reach !== "off") && s.reach === "ready");
  const url = ready ? s.shareUrl : null;
  const words = remote
    ? { title: "remote_connect_title", body: "remote_connect_body", signIn: "remote_connect_sign_in" }
    : { title: "connect_ready_title", body: "connect_ready_body", signIn: "connect_sign_in_body" };
  const access = s.accessSwitch && <AccessSwitch s={s} />;

  return (
    <Page title={t("section_connect")}>
      {url ? (
        // Narrow: the switch, the code, the address, one under another. Medium: the switch over the code and the address
        // side by side. Wide: the code, and beside it the switch over the address.
        <div className="grid grid-cols-1 content-start items-start gap-5 [grid-template-areas:'access'_'code'_'address'] @min-[460px]:grid-cols-[208px_minmax(0,1fr)] @min-[460px]:[grid-template-areas:'access_access'_'code_address'] @min-[680px]:[grid-template-areas:'code_access'_'code_address']">
          {access && <div className="[grid-area:access]">{access}</div>}
          <div className="[grid-area:code] @min-[680px]:row-span-2">
            <Code url={url} />
          </div>
          <div className="[grid-area:address]">
            <Address url={url} {...words} />
          </div>
        </div>
      ) : (
        <div className={`grid items-start gap-5 ${access ? "@min-[680px]:grid-cols-2" : ""}`}>
          {access}
          <NotReady s={s} go={go} />
        </div>
      )}
    </Page>
  );
}

function AccessSwitch({ s }: { s: Snapshot }) {
  const [restarting, setRestarting] = useState(false);
  return (
    <Card>
      <label className="flex items-start gap-4">
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{t("access_title")}</p>
          <p className="text-sm text-base-content/75">{t("access_body")}</p>
          {restarting && <p className="mt-2 text-sm text-secondary">{t("access_restarting")}</p>}
          {s.accessWaits && <p className="mt-2 text-sm text-secondary">{t("access_waits")}</p>}
        </div>
        <input
          type="checkbox"
          className="toggle toggle-success mt-1"
          checked={s.openToOthers}
          disabled={s.accessWaits}
          onChange={async (e) => {
            setRestarting(true);
            await ipc.setAccess(e.target.checked);
            setTimeout(() => setRestarting(false), 4000);
          }}
        />
      </label>
    </Card>
  );
}

function NotReady({ s, go }: { s: Snapshot; go: (section: Section) => void }) {
  if (s.adopted && s.reach !== "off") {
    return (
      <Card>
        <p className="text-base-content/75">{t("adopted_connect_body")}</p>
      </Card>
    );
  }
  switch (s.reach) {
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
    default:
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
  }
}

/** The code a tablet's camera opens: dark on white in both themes, as a camera reads contrast. */
function Code({ url }: { url: string }) {
  const [svg, setSvg] = useState<string | null>(null);
  useEffect(() => {
    ipc.qr(url).then(setSvg);
  }, [url]);
  return (
    <Card className="flex justify-center !p-4">
      {svg ? (
        <div
          role="img"
          aria-label={t("connect_qr_description")}
          className="size-44 rounded-box bg-white p-2.5 [&>svg]:size-full"
          // The app's own QR code, drawn by Rust from the address: no outside markup.
          dangerouslySetInnerHTML={{ __html: svg }}
        />
      ) : (
        <div className="size-44 rounded-box bg-white" />
      )}
    </Card>
  );
}

/** The address, Copy, and how to sign in there. */
function Address({ url, title, body, signIn }: { url: string; title: string; body: string; signIn: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Card>
      <p className="font-display text-xl">{t(title)}</p>
      <p className="mt-1 text-sm text-base-content/75">{t(body)}</p>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Pill className="!text-base @min-[680px]:!text-lg">{shown(url)}</Pill>
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
      </div>
      <div className="mt-4 border-t border-base-300 pt-4">
        <p className="font-semibold">{t("connect_sign_in_title")}</p>
        <p className="text-sm text-base-content/75">{t(signIn)}</p>
      </div>
    </Card>
  );
}
