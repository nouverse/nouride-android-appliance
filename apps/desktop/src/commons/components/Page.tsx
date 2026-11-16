import type { ReactNode } from "react";

/**
 * A section's page: its name, then its content in a column kept to a readable width on a wide window. Its `@container`
 * is what the screens' columns follow (`@min-[680px]:` is about a 960 px window with the sidebar's labels).
 */
export function Page({ title, actions, children, full }: { title: string; actions?: ReactNode; children: ReactNode; full?: boolean }) {
  return (
    <div className={`@container mx-auto flex w-full flex-col gap-5 ${full ? "h-full" : "max-w-[960px]"}`}>
      <header className="flex min-h-10 items-center gap-3">
        <h1 className="flex-1 font-display text-[26px] leading-tight tracking-tight">{title}</h1>
        {actions}
      </header>
      {children}
    </div>
  );
}
