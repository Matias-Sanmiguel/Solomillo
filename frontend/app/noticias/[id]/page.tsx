"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { get, Noticia, fmtFecha } from "@/lib/api";
import { NoticiaImagen } from "../../components/NoticiaImagen";
import { categoriaColor } from "../../components/NoticiaCard";

export default function NoticiaDetallePage({ params }: { params: { id: string } }) {
  const id = params.id;
  const [n, setN] = useState<Noticia | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    get<Noticia>(`/noticias/${id}`)
      .then(setN)
      .catch(() => setN(null))
      .finally(() => setCargando(false));
  }, [id]);

  if (cargando) return <p className="text-muted">Cargando…</p>;
  if (!n) return <p className="text-muted">Noticia no encontrada.</p>;

  return (
    <article className="mx-auto max-w-3xl space-y-5">
      <Link href="/noticias" className="text-sm text-accent hover:underline">
        ← Volver a Noticias
      </Link>

      <div className="flex items-center gap-2">
        <span className={`rounded-full border px-2.5 py-1 text-xs font-semibold uppercase tracking-wide ${categoriaColor(n.categoria)}`}>
          {n.categoria_label}
        </span>
        {n.origen === "SIMULADO" ? <span className="chip">Proyección IA</span> : null}
        <span className="text-xs text-muted">{fmtFecha(n.fecha)}</span>
      </div>

      <h1 className="text-3xl font-bold leading-tight">{n.titulo}</h1>
      <p className="text-lg text-muted">{n.subtitulo}</p>

      <NoticiaImagen data={n.imagen} alto={320} destacada />

      <p className="text-base leading-relaxed text-white/90">{n.resumen}</p>

      <div className="flex flex-wrap gap-2 border-t border-line pt-4">
        {n.tags.map((t) => (
          <span key={t} className="chip">
            {t}
          </span>
        ))}
      </div>
    </article>
  );
}
