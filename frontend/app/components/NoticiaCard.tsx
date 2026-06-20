import Link from "next/link";
import { Noticia, fmtFecha } from "@/lib/api";
import { NoticiaImagen } from "./NoticiaImagen";

export function categoriaColor(nombre: string): string {
  switch (nombre) {
    case "BATACAZO":
      return "border-warn/40 bg-warn/15 text-warn";
    case "RESULTADO_INESPERADO":
      return "border-visit/40 bg-visit/15 text-visit";
    case "FIGURA":
      return "border-accent/40 bg-accent/15 text-accent";
    case "GOLEADA":
      return "border-good/40 bg-good/15 text-good";
    case "RANKING_ELO":
      return "border-local/40 bg-local/15 text-local";
    default:
      return "border-line bg-panel2 text-muted";
  }
}

export function NoticiaCard({ n }: { n: Noticia }) {
  return (
    <Link href={`/noticias/${n.id}`} className="card group flex flex-col gap-3 p-3 transition hover:border-accent/50">
      <NoticiaImagen data={n.imagen} alto={150} />
      <div className="flex items-center gap-2">
        <span className={`rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${categoriaColor(n.categoria)}`}>
          {n.categoria_label}
        </span>
        {n.origen === "SIMULADO" ? (
          <span className="chip text-[10px]">IA</span>
        ) : null}
      </div>
      <div className="flex-1">
        <h3 className="font-semibold leading-snug group-hover:text-accent">{n.titulo}</h3>
        <p className="mt-1 text-sm text-muted">{n.subtitulo}</p>
      </div>
      <div className="flex items-center justify-between text-xs text-muted">
        <span>{fmtFecha(n.fecha)}</span>
        <div className="flex gap-1">
          {n.tags.slice(0, 2).map((t) => (
            <span key={t} className="chip text-[10px]">
              {t}
            </span>
          ))}
        </div>
      </div>
    </Link>
  );
}
