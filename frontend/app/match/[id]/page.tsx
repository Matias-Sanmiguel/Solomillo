"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  get,
  Equipo,
  Partido,
  Prediccion,
  EloPunto,
  fmtFecha,
  pct,
} from "@/lib/api";
import { Crest } from "../../components/Crest";
import { ProbBar, favorito } from "../../components/ProbBar";
import { EloTrend } from "../../components/EloTrend";

export default function MatchPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const [partido, setPartido] = useState<Partido | null>(null);
  const [local, setLocal] = useState<Equipo | null>(null);
  const [visit, setVisit] = useState<Equipo | null>(null);
  const [pred, setPred] = useState<Prediccion | null>(null);
  const [eloL, setEloL] = useState<EloPunto[]>([]);
  const [eloV, setEloV] = useState<EloPunto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const [equipos, partidos] = await Promise.all([
          get<Equipo[]>("/equipos"),
          get<Partido[]>("/partidos"),
        ]);
        const p = partidos.find((x) => x.id === id) ?? null;
        setPartido(p);
        if (p) {
          const l = equipos.find((e) => e.id === p.local_id) ?? null;
          const v = equipos.find((e) => e.id === p.visitante_id) ?? null;
          setLocal(l);
          setVisit(v);
          if (l) get<EloPunto[]>(`/ml/elo/${l.id}/historial`).then(setEloL).catch(() => {});
          if (v) get<EloPunto[]>(`/ml/elo/${v.id}/historial`).then(setEloV).catch(() => {});
        }
        const pr = await get<Prediccion>(`/ml/predicciones/${id}`).catch(() => null);
        if (pr) setPred(pr);
        else setError("sin-modelo");
      } catch {
        setError("error");
      }
    })();
  }, [id]);

  if (!partido) return <p className="text-muted">Cargando partido…</p>;

  const fav = pred ? favorito(pred.probabilidades) : null;
  const finalizado = partido.estado === "FINALIZADO";

  return (
    <div className="space-y-5">
      <Link href="/" className="text-sm text-accent hover:underline">
        ← Predicciones
      </Link>

      <div className="card">
        <div className="mb-1 text-xs text-muted">{fmtFecha(partido.fecha_hora)}</div>
        <div className="flex items-center justify-between gap-4">
          <Side nombre={local?.nombre ?? `#${partido.local_id}`} escudo={local?.escudo} />
          <div className="text-center">
            {finalizado ? (
              <div className="text-4xl font-bold tabular-nums">
                {partido.goles_local}<span className="text-muted"> · </span>{partido.goles_visitante}
              </div>
            ) : (
              <div className="text-sm font-medium text-muted">vs</div>
            )}
            <div className="mt-1 chip">{estadoLabel(partido.estado)}</div>
          </div>
          <Side nombre={visit?.nombre ?? `#${partido.visitante_id}`} escudo={visit?.escudo} right />
        </div>
      </div>

      <div className="card">
        <h2 className="mb-3 text-sm font-semibold text-muted">Predicción del modelo</h2>
        {pred ? (
          <>
            <ProbBar p={pred.probabilidades} />
            <div className="mt-4 flex items-center justify-between text-sm">
              <span className="text-muted">
                Favorito: <b className="text-white">{fav!.label}</b>
              </span>
              <span className="chip">confianza {pct(fav!.conf)}</span>
              <span className="text-muted">modelo v{pred.modelo_version}</span>
            </div>
            {finalizado && (
              <p className="mt-3 text-xs text-muted">
                Resultado real:{" "}
                <b className="text-white">
                  {resultadoReal(partido)}
                </b>{" "}
                {aciertoLabel(partido, pred)}
              </p>
            )}
          </>
        ) : (
          <p className="text-sm text-muted">
            {error === "sin-modelo" ? "No hay modelo entrenado todavía." : "Sin predicción."}
          </p>
        )}
      </div>

      <div className="card">
        <h2 className="mb-3 text-sm font-semibold text-muted">Evolución de Elo</h2>
        <EloTrend
          series={[
            { nombre: local?.nombre ?? "Local", color: "#22d3ee", data: eloL },
            { nombre: visit?.nombre ?? "Visita", color: "#f472b6", data: eloV },
          ]}
        />
      </div>
    </div>
  );
}

function Side({ nombre, escudo, right }: { nombre: string; escudo?: string; right?: boolean }) {
  return (
    <div className={`flex flex-1 items-center gap-3 ${right ? "flex-row-reverse text-right" : ""}`}>
      <Crest src={escudo} size={48} />
      <span className="text-lg font-bold">{nombre}</span>
    </div>
  );
}

function estadoLabel(e: Partido["estado"]) {
  return e === "FINALIZADO" ? "Finalizado" : e === "EN_VIVO" ? "En vivo" : "Programado";
}

function resultadoReal(p: Partido) {
  const gl = p.goles_local ?? 0;
  const gv = p.goles_visitante ?? 0;
  return gl > gv ? "Ganó local" : gl < gv ? "Ganó visita" : "Empate";
}

function aciertoLabel(p: Partido, pred: Prediccion) {
  const gl = p.goles_local ?? 0;
  const gv = p.goles_visitante ?? 0;
  const realIdx = gl > gv ? 0 : gl === gv ? 1 : 2;
  const probs = [pred.probabilidades.local, pred.probabilidades.empate, pred.probabilidades.visitante];
  const predIdx = probs.indexOf(Math.max(...probs));
  return predIdx === realIdx ? "✓ acertó" : "✗ falló";
}
