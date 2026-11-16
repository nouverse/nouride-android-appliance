import icon from "../mascot/hello.webp";

/** The cat on its disc, at the top of the sidebar: lavender, yellow in the Dev build as its icon is. */
export function CatDisc({ dev, size = 44 }: { dev: boolean; size?: number }) {
  return (
    <span
      className={`grid shrink-0 place-items-center overflow-hidden rounded-full ${dev ? "bg-[#FFD84D]" : "bg-secondary/15"}`}
      style={{ width: size, height: size }}
    >
      <img src={icon} alt="" className="w-auto translate-y-[9%]" style={{ height: size * 0.82 }} draggable={false} />
    </span>
  );
}
