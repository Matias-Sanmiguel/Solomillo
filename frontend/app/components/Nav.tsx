"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Predicciones" },
  { href: "/noticias", label: "Noticias" },
  { href: "/clasificacion", label: "Clasificación" },
  { href: "/calendario", label: "Calendario" },
  { href: "/goleadores", label: "Goleadores" },
  { href: "/compare", label: "Comparar" },
  { href: "/prode", label: "Prode" },
  { href: "/prode/ranking", label: "Ranking Prode" },
  { href: "/models", label: "Modelos" },
  { href: "/elo", label: "Ranking Elo" },
];

export function Nav() {
  const path = usePathname();
  return (
    <nav className="flex gap-1 rounded-xl border border-line bg-panel/60 p-1">
      {LINKS.map((l) => {
        const active = l.href === "/" ? path === "/" : path.startsWith(l.href);
        return (
          <Link key={l.href} href={l.href} className={`navlink ${active ? "navlink-active" : ""}`}>
            {l.label}
          </Link>
        );
      })}
    </nav>
  );
}
