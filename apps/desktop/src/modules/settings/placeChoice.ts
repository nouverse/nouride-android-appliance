import type { Place } from "../../commons/lib/ipc";

/** Which answer Settings' choice shows: the place in use, or Another computer while its address section is open. */
export function shownPlace(place: Place | null, asking: boolean): Place {
  return asking ? "remote" : (place ?? "here");
}

export interface Press {
  /** The address section is open after the press. */
  asking: boolean;
  /** Nouride moves back to this computer. */
  chooseHere: boolean;
}

/**
 * A press on the choice. Another computer opens the address section under it (nothing changes until Connect works);
 * This computer closes the section, and moves Nouride back here only when it runs elsewhere.
 */
export function pressPlace(place: Place | null, picked: Place): Press {
  return picked === "remote" ? { asking: true, chooseHere: false } : { asking: false, chooseHere: place !== "here" };
}
