import { Mascot } from "@nouride-home/ui-kit/components";
import { t } from "../../commons/lib/words";

/** The dashboard's window while it opens: the riding cat, before the dashboard's own page replaces it. */
export function Opening() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 bg-app">
      <Mascot pose="ride" size={140} />
      <p className="font-display text-xl">{t("dashboard_opening")}</p>
    </div>
  );
}
