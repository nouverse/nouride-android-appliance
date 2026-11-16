import type { ReactNode } from "react";

/** The one main button of a view: a full-width peach pill, ink on it, with its faint glow. */
export function MainButton({ children, onClick, disabled }: { children: ReactNode; onClick: () => void; disabled?: boolean }) {
  return (
    <button type="button" className="btn btn-primary btn-lg w-full font-semibold" onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

/** Words that act, for what is rarely pressed: never a second main button. */
export function TextButton({ children, onClick, tone }: { children: ReactNode; onClick: () => void; tone?: "warn" }) {
  return (
    <button type="button" className={`btn btn-ghost btn-sm font-semibold ${tone === "warn" ? "text-warning" : "text-secondary"}`} onClick={onClick}>
      {children}
    </button>
  );
}
