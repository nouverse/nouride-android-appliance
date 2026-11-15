import type { ReactNode } from "react";

/** An address, in DM Mono in a soft pill: the one place monospace shows outside Technical Logs. */
export function Pill({ children, large }: { children: ReactNode; large?: boolean }) {
  return (
    <span className={`inline-block rounded-full bg-secondary/10 px-3 py-1 font-mono text-base-content ${large ? "text-lg" : "text-sm"} break-all`}>
      {children}
    </span>
  );
}
