import type { ReactNode } from "react";

/** A round card over the window, the app's own look; it does not float, as a card in a dialog does not. */
export function Dialog({ children, onClose }: { children: ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-20 grid place-items-center bg-base-content/30 p-5 animate-fade" onClick={onClose}>
      <div className="w-full rounded-box bg-base-100 p-6" onClick={(e) => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}
