import Link from "next/link";
import { Noticia, fmtFecha } from "@/lib/api";
import { NoticiaImagen } from "./NoticiaImagen";
import { categoriaColor } from "./NoticiaCard";

export function NoticiaHero({ n }: { n: Noticia }) {
  return (
    <Link
      href={`/noticias/${n.id}`}
      className="card group grid gap-5 p-4 transition hover:border-accent/50 md:grid-cols-2 md:items-center"
    >
      <NoticiaImagen data={n.imagen} alto={280} destacada />
      <div>
        <div className="mb-3 flex items-center gap-2">
          <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold uppercase tracking-wide ${categoriaColor(n.categoria)}`}>
            {n.categoria_label}
          </span>
          {n.origen === "SIMULADO" ? <span className="chip">Proyección IA</span> : <span className="chip">En vivo</span>}
        </div>
        <h2 className="text-2xl font-bold leading-tight group-hover:text-accent md:text-3xl">{n.titulo}</h2>
        <p className="mt-2 text-lg text-muted">{n.subtitulo}</p>
        <p className="mt-3 text-sm leading-relaxed text-white/80">{n.resumen}</p>
        <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-muted">
          <span>{fmtFecha(n.fecha)}</span>
          {n.tags.map((t) => (
            <span key={t} className="chip">
              {t}
            </span>
          ))}
        </div>
      </div>
    </Link>
  );
}
