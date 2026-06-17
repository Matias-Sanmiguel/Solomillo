"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { API, WS, get, Equipo, Partido, Prediccion, fmtFecha } from "@/lib/api";
import { Crest } from "./components/Crest";
import { ProbBar, favorito } from "./components/ProbBar";

export default function Board() {
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [partidos, setPartidos] = useState<Partido[]>([]);
  const [preds, setPreds] = useState<Prediccion[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(true);

  const cargar = () => {
    Promise.all([get<Equipo[]>("/equipos"), get<Partido[]>("/partidos")])
      .then(([e, p]) => {
        setEquipos(e);
        setPartidos(p);
      })
      .catch(() => {});
    get<Prediccion[]>("/ml/predicciones")
      .then((ps) => {
        setPreds(ps);
        setError(null);
      })
      .catch(() => setError("sin-modelo"))
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    cargar();
  }, []);

  useEffect(() => {
    let ws: WebSocket | null = null;
    try {
      ws = new WebSocket(WS);
      ws.onmessage = () => cargar();
    } catch {
      /* ws opcional */
    }
    return () => ws?.close();
  }, []);

  const eq = useMemo(() => {
    const m = new Map<number, Equipo>();
    equipos.forEach((e) => m.set(e.id, e));
    return m;
  }, [equipos]);

  const partidoOf = useMemo(() => {
    const m = new Map<number, Partido>();
    partidos.forEach((p) => m.set(p.id, p));
    return m;
  }, [partidos]);

  if (cargando) return <p className="text-muted">Cargando predicciones…</p>;

  if (error === "sin-modelo")
    return (
      <div className="card max-w-xl">
        <h2 className="mb-2 text-lg font-semibold">Todavía no hay un modelo entrenado</h2>
        <p className="mb-4 text-sm text-muted">
          Entrená el modelo de resultados para generar predicciones de los próximos partidos.
        </p>
        <code className="block rounded-lg bg-panel2 p-3 text-xs text-accent">
          POST {API}/ml/modelos/entrenar
        </code>
        <p className="mt-3 text-xs text-muted">
          Requiere token con rol <b>cientifico_datos</b> o <b>admin_modelos_ia</b>.
        </p>
      </div>
    );

  return (
    <div>
      <div className="mb-5 flex items-baseline justify-between">
        <h2 className="text-lg font-semibold">Próximos partidos</h2>
        <span className="chip">{preds.length} predicciones</span>
      </div>

      {preds.length === 0 && <p className="text-muted">No hay partidos próximos para predecir.</p>}

      <div className="grid gap-4 sm:grid-cols-2">
        {preds.map((pred) => {
          const p = partidoOf.get(pred.partido_id);
          const local = p ? eq.get(p.local_id) : undefined;
          const visit = p ? eq.get(p.visitante_id) : undefined;
          const fav = favorito(pred.probabilidades);
          return (
            <Link
              key={pred.partido_id}
              href={`/match/${pred.partido_id}`}
              className="card transition hover:border-accent/60 hover:bg-panel2/60"
            >
              <div className="mb-3 flex items-center justify-between text-xs text-muted">
                <span>{fmtFecha(p?.fecha_hora ?? null)}</span>
                <span className="chip">
                  {fav.label} · {Math.round(fav.conf * 100)}%
                </span>
              </div>

              <div className="mb-4 flex items-center justify-between gap-2">
                <Team nombre={local?.nombre ?? `#${p?.local_id}`} escudo={local?.escudo} />
                <span className="text-xs font-medium text-muted">vs</span>
                <Team nombre={visit?.nombre ?? `#${p?.visitante_id}`} escudo={visit?.escudo} right />
              </div>

              <ProbBar p={pred.probabilidades} />
            </Link>
          );
        })}
      </div>
    </div>
  );
}

function Team({ nombre, escudo, right }: { nombre: string; escudo?: string; right?: boolean }) {
  return (
    <div className={`flex flex-1 items-center gap-2 ${right ? "flex-row-reverse text-right" : ""}`}>
      <Crest src={escudo} size={28} />
      <span className="truncate text-sm font-semibold">{nombre}</span>
    </div>
  );
}
