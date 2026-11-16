import { CatDisc } from "@nouride-home/ui-kit/components";
import { House, type LucideIcon, QrCode, RefreshCw, ScrollText, Settings } from "lucide-react";
import { dotted, type Section, SECTIONS, type SidebarShape } from "../lib/sections";
import type { Snapshot } from "../lib/ipc";
import { t } from "../lib/words";

const ICONS: Record<Section, LucideIcon> = { home: House, connect: QrCode, versions: RefreshCw, settings: Settings, logs: ScrollText };

/**
 * The sections down the left, on a slightly tinted surface: the cat and the app's name on top, Technical Logs set
 * apart at the bottom, the open one in the soft lavender pill and a dot where an update waits. Folded to a rail of
 * icons in a narrow window, each with its name as a tooltip and for a screen reader. On a Mac it runs up under the
 * window's buttons, so its top leaves them room.
 */
export function Sidebar({ s, open, onOpen, shape, mac }: { s: Snapshot; open: Section; onOpen: (section: Section) => void; shape: SidebarShape; mac: boolean }) {
  const rail = shape === "rail";
  const item = ({ section, words }: (typeof SECTIONS)[number]) => {
    const Icon = ICONS[section];
    const here = open === section;
    const dot = dotted(section, s);
    return (
      <li key={section} className={rail ? "tooltip tooltip-right" : ""} data-tip={rail ? t(words) : undefined}>
        <button
          type="button"
          onClick={() => onOpen(section)}
          aria-label={rail ? t(words) : undefined}
          aria-current={here ? "page" : undefined}
          data-e2e={`section-${section}`}
          className={`relative flex w-full items-center rounded-full text-sm font-semibold transition-colors ${
            rail ? "size-11 justify-center" : "gap-2.5 py-2 pl-3 pr-5 text-left leading-tight"
          } ${here ? "bg-secondary/15 text-secondary" : "text-base-content/70 hover:bg-base-content/5"}`}
        >
          <Icon className="size-[18px] shrink-0" strokeWidth={2} />
          {!rail && <span className="min-w-0 flex-1">{t(words)}</span>}
          {dot && <span className={`absolute size-2 rounded-full bg-accent ${rail ? "right-2 top-2" : "right-2.5 top-1/2 -translate-y-1/2"}`} aria-label={t("update_waiting")} />}
        </button>
      </li>
    );
  };
  return (
    <aside
      className={`flex h-full shrink-0 flex-col border-r border-base-300 bg-base-200/70 ${rail ? "w-16 items-center px-2.5" : "w-[228px] px-2.5"} ${
        mac ? "pt-11" : "pt-4"
      } pb-3`}
    >
      <div className={`flex items-center gap-2.5 pb-5 ${rail ? "justify-center" : "px-1"}`}>
        <CatDisc dev={s.dev} size={rail ? 40 : 44} />
        {!rail && (
          <div className="min-w-0">
            <p className="font-display text-[17px] leading-tight tracking-tight">{t("app_name")}</p>
            {s.dev && <span className="mt-0.5 inline-block rounded-full bg-[#FFD84D] px-2 text-[11px] font-bold text-[#22202c]">{t("dev_badge")}</span>}
          </div>
        )}
      </div>
      <nav className="flex min-h-0 w-full flex-1 flex-col" aria-label={t("sections")}>
        <ul className="flex flex-col gap-1">{SECTIONS.filter((i) => !i.apart).map(item)}</ul>
        <ul className="mt-auto flex flex-col gap-1 border-t border-base-300 pt-3">{SECTIONS.filter((i) => i.apart).map(item)}</ul>
      </nav>
    </aside>
  );
}
