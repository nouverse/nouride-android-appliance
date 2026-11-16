import type { ReactNode } from "react";

/** A card that floats: 22px corners, a hairline and a soft shadow in the theme's shade. */
export function Card({ children, className = "", tone }: { children: ReactNode; className?: string; tone?: "good" | "warn" | "place" }) {
  const tint =
    tone === "good"
      ? "bg-success/10 border-success/20"
      : tone === "warn"
        ? "bg-accent/10 border-accent/25"
        : tone === "place"
          ? "bg-secondary/10 border-secondary/20"
          : "bg-base-100 border-base-300";
  return <div className={`rounded-box border shadow-soft p-5 ${tint} ${className}`}>{children}</div>;
}

/** The card that says how Nouride is, lit like the dashboard's greeting: the mood's colour at its left, peach at its right. */
export function Hero({ children, tone }: { children: ReactNode; tone: "calm" | "busy" | "good" | "warn" }) {
  const mood = tone === "good" ? "border-success/30" : tone === "warn" ? "border-accent/40" : "border-base-300";
  const lit = tone === "warn" ? "bg-accent/10" : tone === "good" ? "bg-success/10" : "hero-soft";
  return <div className={`rounded-box border shadow-soft p-6 ${mood} ${lit}`}>{children}</div>;
}
