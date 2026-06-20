"use client";
import { useEffect, useMemo, useState } from "react";
import { get, Noticia, CategoriaNoticia, PrediccionAcierto } from "@/lib/api";
import { NoticiaHero } from "../components/NoticiaHero";
import { NoticiaCard } from "../components/NoticiaCard";
import { CategoriaFiltros } from "../components/CategoriaFiltros";
import { PrediccionVsRealidad } from "../components/PrediccionVsRealidad";

export default function NoticiasPage() {
  const [noticias, setNoticias] = useState<Noticia[]>([]);
  const [categorias, setCategorias] = useState<CategoriaNoticia[]>([]);
  const [aciertos, setAciertos] = useState<PrediccionAcierto[]>([]);
  const [filtro, setFiltro] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    Promise.all([
      get<Noticia[]>("/noticias"),
      get<CategoriaNoticia[]>("/noticias/categorias"),
      get<PrediccionAcierto[]>("/predicciones/aciertos").catch(() => []),
    ])
      .then(([ns, cs, ac]) => {
        setNoticias(ns);
        setCategorias(cs);
        setAciertos(ac);
      })
      .catch(() => {})
      .finally(() => setCargando(false));
  }, []);

  const filtradas = useMemo(
    () => (filtro ? noticias.filter((n) => n.categoria === filtro) : noticias),
    [noticias, filtro]
  );

  const hero = filtradas[0];
  const resto = filtradas.slice(1);

  if (cargando) return <p className="text-muted">Cargando noticias…</p>;

  return (
    <div className="space-y-8">
      <section className="space-y-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Noticias del Mundial</h1>
            <p className="text-sm text-muted">Generadas automáticamente a partir de los datos del torneo</p>
          </div>
        </div>
        <CategoriaFiltros
          categorias={categorias}
          value={filtro}
          total={noticias.length}
          onChange={setFiltro}
        />
      </section>

      {noticias.length === 0 ? (
        <p className="text-sm text-muted">
          Todavía no hay noticias. Se generan al finalizar partidos o regenerando el feed.
        </p>
      ) : (
        <>
          {hero ? <NoticiaHero n={hero} /> : null}
          {resto.length > 0 ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {resto.map((n) => (
                <NoticiaCard key={n.id} n={n} />
              ))}
            </div>
          ) : null}
        </>
      )}

      <section className="space-y-4 border-t border-line pt-8">
        <div>
          <h2 className="text-xl font-bold tracking-tight">Lo que predijo la IA</h2>
          <p className="text-sm text-muted">Probabilidad previa vs. resultado real</p>
        </div>
        <PrediccionVsRealidad items={aciertos} />
      </section>
    </div>
  );
}
