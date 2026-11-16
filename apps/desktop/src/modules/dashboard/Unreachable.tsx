import { MainButton, Mascot } from "@nouride-home/ui-kit/components";
import { ipc } from "../../commons/lib/ipc";
import { t } from "../../commons/lib/words";

/**
 * Shown in the dashboard's window when Nouride does not answer, in place of the WebView's own error page: the cat and
 * Try again, which asks the app to open the dashboard again: it loads it once Nouride answers, and this page again
 * until then.
 */
export function Unreachable() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8 text-center bg-app">
      <Mascot pose="unplugged" size={160} />
      <h1 className="font-display text-2xl tracking-tight">{t("dashboard_unreachable_title")}</h1>
      <p className="max-w-sm text-base-content/75">{t("dashboard_unreachable_body")}</p>
      <div className="w-64">
        <MainButton onClick={() => ipc.openDashboard()}>{t("action_retry")}</MainButton>
      </div>
      <a className="btn btn-ghost btn-sm" href="nouridehome://home">
        {t("back_to_home")}
      </a>
    </div>
  );
}
