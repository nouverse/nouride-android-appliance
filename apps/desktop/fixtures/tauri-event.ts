// Stands in for `@tauri-apps/api/event` in the layout pictures: `screen` opens the section named in the address
// (`?section=connect`), as the menu bar would; nothing else is ever sent.
export type UnlistenFn = () => void;

export async function listen<T>(event: string, handler: (event: { payload: T }) => void): Promise<UnlistenFn> {
  const section = new URLSearchParams(location.search).get("section");
  if (event === "screen" && section) setTimeout(() => handler({ payload: section as T }), 0);
  return () => {};
}
