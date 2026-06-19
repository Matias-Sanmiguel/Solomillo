"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { API, WS, get, Equipo, Partido, Prediccion, fmtFecha } from "@/lib/api";
import { Crest } from "./components/Crest";
import { ProbBar, favorito } from "./components/ProbBar";
import { SeguirEquipo } from "./components/SeguirEquipo";

async function demoPost(path: string) {
  await fetch(`${API}${path}`, { method: "POST" });
}

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
        <h2 className="mb-2 text-lg font-semibold">
          Todavía no hay un modelo entrenado
        </h2>
        <p className="mb-4 text-sm text-muted">
          Entrená el modelo de resultados para generar predicciones de los
          próximos partidos.
        </p>
        <code className="block rounded-lg bg-panel2 p-3 text-xs text-accent">
          POST {API}/ml/modelos/entrenar
        </code>
        <p className="mt-3 text-xs text-muted">
          Requiere token con rol <b>cientifico_datos</b> o{" "}
          <b>admin_modelos_ia</b>.
        </p>
      </div>
    );

  const enVivo = partidos.filter((p) => p.estado === "EN_VIVO");

  return (
    <div>
      {enVivo.length > 0 && (
        <div className="mb-6">
          <div className="mb-3 flex items-center gap-2">
            <span className="flex items-center gap-1.5 text-sm font-semibold text-good">
              <span className="relative flex h-2.5 w-2.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-good opacity-75" />
                <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-good" />
              </span>
              En vivo
            </span>
            <span className="chip">
              {enVivo.length} partido{enVivo.length !== 1 ? "s" : ""}
            </span>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {enVivo.map((p) => {
              const local = eq.get(p.local_id ?? -1);
              const visit = eq.get(p.visitante_id ?? -1);
              const pred = preds.find((pr) => pr.partido_id === p.id);
              return (
                <div
                  key={p.id}
                  className="card border-good/30 bg-panel2/60 flex flex-col gap-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold uppercase tracking-widest text-good">
                      EN VIVO
                    </span>
                    {pred && (
                      <span className="chip">
                        {favorito(pred.probabilidades).label} ·{" "}
                        {Math.round(favorito(pred.probabilidades).conf * 100)}%
                      </span>
                    )}
                  </div>

                  <div className="flex items-center justify-between gap-2">
                    <Team
                      nombre={local?.nombre ?? `#${p.local_id}`}
                      escudo={local?.escudo}
                    />
                    <div className="text-center">
                      <div className="text-3xl font-bold tabular-nums text-white">
                        {p.goles_local ?? 0}
                        <span className="mx-1 text-muted">–</span>
                        {p.goles_visitante ?? 0}
                      </div>
                    </div>
                    <Team
                      nombre={visit?.nombre ?? `#${p.visitante_id}`}
                      escudo={visit?.escudo}
                      right
                    />
                  </div>

                  {pred && <ProbBar p={pred.probabilidades} />}

                  <div className="flex flex-wrap gap-2">
                    <button
                      onClick={() => demoPost(`/demo/gol/${p.id}?equipo=local`)}
                      className="chip cursor-pointer hover:border-local/60 hover:text-local transition"
                    >
                      ⚽ Gol local
                    </button>
                    <button
                      onClick={() =>
                        demoPost(`/demo/gol/${p.id}?equipo=visitante`)
                      }
                      className="chip cursor-pointer hover:border-visit/60 hover:text-visit transition"
                    >
                      ⚽ Gol visita
                    </button>
                    <button
                      onClick={() =>
                        demoPost(`/demo/tarjeta/${p.id}?color=amarilla`)
                      }
                      className="chip cursor-pointer hover:border-warn/60 hover:text-warn transition"
                    >
                      🟨 Tarjeta
                    </button>
                    <button
                      onClick={() => demoPost(`/demo/finalizar/${p.id}`)}
                      className="chip cursor-pointer hover:border-muted hover:text-white transition"
                    >
                      🏁 Finalizar
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div className="mb-5 flex items-baseline justify-between">
        <h2 className="text-lg font-semibold">Próximos partidos</h2>
        <span className="chip">
          {
            preds.filter(
              (pr) =>
                partidos.find((p) => p.id === pr.partido_id)?.estado ===
                "PROGRAMADO",
            ).length
          }{" "}
          predicciones
        </span>
      </div>

      {preds.length === 0 && (
        <p className="text-muted">No hay partidos próximos para predecir.</p>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {preds
          .filter(
            (pred) => partidoOf.get(pred.partido_id)?.estado === "PROGRAMADO",
          )
          .map((pred) => {
            const p = partidoOf.get(pred.partido_id);
            const local = p ? eq.get(p.local_id ?? -1) : undefined;
            const visit = p ? eq.get(p.visitante_id ?? -1) : undefined;
            const fav = favorito(pred.probabilidades);
            return (
              <Link
                key={pred.partido_id}
                href={`/match/${pred.partido_id}`}
                className="card flex flex-col transition hover:border-accent/60 hover:bg-panel2/60"
              >
                <div className="mb-3 flex items-center justify-between text-xs text-muted">
                  <span>{fmtFecha(p?.fecha_hora ?? null)}</span>
                  <span className="chip">
                    {fav.label} · {Math.round(fav.conf * 100)}%
                  </span>
                </div>

                <div className="mb-4 flex items-center justify-between gap-2">
                  <Team
                    nombre={local?.nombre ?? `#${p?.local_id}`}
                    escudo={local?.escudo}
                  />
                  <span className="text-xs font-medium text-muted">vs</span>
                  <Team
                    nombre={visit?.nombre ?? `#${p?.visitante_id}`}
                    escudo={visit?.escudo}
                    right
                  />
                </div>

                <ProbBar p={pred.probabilidades} />

                <div className="mt-3 flex justify-between">
                  <div className="flex flex-col items-start gap-2">
                    {local && <SeguirEquipo equipoId={local.id} />}
                  </div>

                  <div className="flex flex-col items-end gap-2">
                    {visit && <SeguirEquipo equipoId={visit.id} />}
                  </div>
                </div>
              </Link>
            );
          })}
      </div>
    </div>
  );
}

function Team({
  nombre,
  escudo,
  right,
}: {
  nombre: string;
  escudo?: string;
  right?: boolean;
}) {
  return (
    <div
      className={`flex flex-1 items-center gap-2 ${right ? "flex-row-reverse text-right" : ""}`}
    >
      <Crest src={escudo} size={28} />
      <span className="truncate text-sm font-semibold">{nombre}</span>
    </div>
  );
}
