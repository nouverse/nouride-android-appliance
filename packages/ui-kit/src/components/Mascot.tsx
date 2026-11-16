import hello from "../mascot/hello.webp";
import pointing from "../mascot/pointing.webp";
import ride from "../mascot/ride.webp";
import thumbs from "../mascot/thumbs.webp";
import unplugged from "../mascot/unplugged.webp";
import update from "../mascot/update.webp";

/**
 * Nouride's soft mascot, the poses Android's Nouride Home draws, each meaning what it means in the dashboard: waving
 * when off, riding while something is on its way (it floats gently), thumbs up when running, sad beside an unplugged
 * cable when something failed, pointing at what needs the person, the wrench for a new version.
 */
const POSES = { hello, pointing, ride, thumbs, unplugged, update } as const;

export type Pose = keyof typeof POSES;

export function Mascot({ pose, size = 96, className = "" }: { pose: Pose; size?: number; className?: string }) {
  return (
    <img
      src={POSES[pose]}
      alt=""
      draggable={false}
      style={{ height: size, width: "auto" }}
      className={`select-none ${pose === "ride" ? "animate-float" : ""} ${className}`}
    />
  );
}
