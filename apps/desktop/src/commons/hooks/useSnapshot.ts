import { useEffect, useState } from "react";
import { ipc, type Snapshot } from "../lib/ipc";

/** The app's state, as Rust last sent it: asked once, then followed. */
export function useSnapshot(): Snapshot | null {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  useEffect(() => {
    let stop: (() => void) | undefined;
    let alive = true;
    ipc.onState((s) => alive && setSnapshot(s)).then((unlisten) => {
      if (alive) stop = unlisten;
      else unlisten();
    });
    ipc.state().then((s) => alive && setSnapshot(s));
    return () => {
      alive = false;
      stop?.();
    };
  }, []);
  return snapshot;
}
