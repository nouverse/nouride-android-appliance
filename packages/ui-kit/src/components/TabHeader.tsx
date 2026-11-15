import icon from "../mascot/hello.webp";

/** Every tab opens the same way: the cat on its disc and the tab's name, so no tab jumps in size. Yellow in the Dev build. */
export function TabHeader({ title, dev }: { title: string; dev: boolean }) {
  return (
    <header className="flex items-center gap-3 px-1 pb-4">
      <span className={`grid size-11 place-items-center overflow-hidden rounded-full ${dev ? "bg-[#FFD84D]" : "bg-secondary/15"}`}>
        <img src={icon} alt="" className="h-9 w-auto translate-y-1" draggable={false} />
      </span>
      <h1 className="font-display text-2xl tracking-tight">{title}</h1>
    </header>
  );
}
