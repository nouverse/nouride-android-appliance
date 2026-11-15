import { Ellipsis, House, QrCode } from "lucide-react";
import type { Tab } from "../lib/screens";
import { t } from "../lib/words";

const TABS: { tab: Tab; icon: typeof House; words: string }[] = [
  { tab: "home", icon: House, words: "tab_home" },
  { tab: "connect", icon: QrCode, words: "tab_connect" },
  { tab: "more", icon: Ellipsis, words: "tab_more" },
];

/** Three tabs along the bottom, a floating card; the open one in a soft lavender pill, a dot on More when it needs the person. */
export function TabBar({ open, onOpen, moreDot }: { open: Tab; onOpen: (tab: Tab) => void; moreDot: boolean }) {
  return (
    <nav className="mx-4 mb-4 grid grid-cols-3 rounded-box border border-base-300 bg-base-100 p-1.5 shadow-soft">
      {TABS.map(({ tab, icon: Icon, words }) => (
        <button
          key={tab}
          type="button"
          onClick={() => onOpen(tab)}
          className={`relative flex flex-col items-center gap-0.5 rounded-box py-1.5 text-xs font-semibold ${
            open === tab ? "bg-secondary/15 text-secondary" : "text-base-content/65"
          }`}
          aria-current={open === tab ? "page" : undefined}
        >
          <Icon className="size-5" strokeWidth={2} />
          {t(words)}
          {tab === "more" && moreDot && <span className="absolute right-[34%] top-1.5 size-2 rounded-full bg-accent" />}
        </button>
      ))}
    </nav>
  );
}
