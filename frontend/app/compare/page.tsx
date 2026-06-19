"use client";
import { useEffect, useState } from "react";
import { get, Equipo } from "@/lib/api";
import { CompareSelector } from "../components/CompareSelector";
import { StatsRadar } from "../components/StatsRadar";
import { Crest } from "../components/Crest";

type Stat = { metrica: string; valor: number; torneo_id: number };
type Jugador = { id: number; nombre: string; posicion: string; numero: number };

type StatRow = {
  metrica: string;
  a: number | null;
  b: number | null;
};

function buildRows(statsA: Stat[], statsB: Stat[]): StatRow[] {
  const metricas = Array.from(
    new Set([...statsA.map((s) => s.metrica), ...statsB.map((s) => s.metrica)])
  ).sort();
  const mapA = new Map(statsA.map((s) => [s.metrica, s.valor]));
  const mapB = new Map(statsB.map((s) => [s.metrica, s.valor]));
  return metricas.map((m) => ({
    metrica: m,
    a: mapA.get(m) ?? null,
    b: mapB.get(m) ?? null,
  }));
}

// Etiqueta legible + orden "futbolero" para la tabla de equipos.
const LABELS: Record<string, string> = {
  partidos_jugados: "Partidos jugados",
  victorias: "Victorias",
  empates: "Empates",
  derrotas: "Derrotas",
  goles_favor: "Goles a favor",
  goles_contra: "Goles en contra",
  puntos: "Puntos",
  tarjetas_amarillas: "Tarjetas amarillas",
  tarjetas_rojas: "Tarjetas rojas",
};
const ORDEN_EQUIPO = Object.keys(LABELS);

const val = (rows: StatRow[], metrica: string, lado: "a" | "b") =>
  rows.find((r) => r.metrica === metrica)?.[lado] ?? null;

/** Promedio de gol por partido, redondeado a 1 decimal (null si no hay partidos). */
function promedio(gf: number | null, pj: number | null): number | null {
  if (!pj) return null;
  return Math.round(((gf ?? 0) / pj) * 10) / 10;
}

/**
 * Filas para la tabla. Para equipos: métricas ordenadas con etiqueta en español + filas
 * derivadas (diferencia de gol, promedio de gol). Para jugadores: las métricas crudas.
 */
function displayRows(rows: StatRow[], modo: "equipos" | "jugadores"): StatRow[] {
  if (modo !== "equipos") {
    return rows.map((r) => ({ ...r, metrica: r.metrica.replace(/_/g, " ") }));
  }
  const base = ORDEN_EQUIPO.filter((m) => rows.some((r) => r.metrica === m)).map(
    (m) => ({ metrica: LABELS[m], a: val(rows, m, "a"), b: val(rows, m, "b") })
  );

  const derivadas: StatRow[] = [];
  const gfA = val(rows, "goles_favor", "a");
  const gfB = val(rows, "goles_favor", "b");
  const gcA = val(rows, "goles_contra", "a");
  const gcB = val(rows, "goles_contra", "b");
  if (gfA !== null || gfB !== null) {
    derivadas.push({
      metrica: "Diferencia de gol",
      a: gfA !== null || gcA !== null ? (gfA ?? 0) - (gcA ?? 0) : null,
      b: gfB !== null || gcB !== null ? (gfB ?? 0) - (gcB ?? 0) : null,
    });
    derivadas.push({
      metrica: "Promedio de gol",
      a: promedio(gfA, val(rows, "partidos_jugados", "a")),
      b: promedio(gfB, val(rows, "partidos_jugados", "b")),
    });
  }
  return [...base, ...derivadas];
}

export default function ComparePage() {
  const [modo, setModo] = useState<"equipos" | "jugadores">("equipos");
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [jugadoresA, setJugadoresA] = useState<Jugador[]>([]);
  const [jugadoresB, setJugadoresB] = useState<Jugador[]>([]);

  const [eqA, setEqA] = useState<Equipo | null>(null);
  const [eqB, setEqB] = useState<Equipo | null>(null);
  const [jugA, setJugA] = useState<Jugador | null>(null);
  const [jugB, setJugB] = useState<Jugador | null>(null);

  const [statsA, setStatsA] = useState<Stat[]>([]);
  const [statsB, setStatsB] = useState<Stat[]>([]);
  const [cargando, setCargando] = useState(false);

  useEffect(() => {
    get<Equipo[]>("/equipos").then(setEquipos).catch(() => {});
  }, []);

  useEffect(() => {
    if (eqA)
      get<Jugador[]>(`/equipos/${eqA.id}/jugadores`)
        .then(setJugadoresA)
        .catch(() => setJugadoresA([]));
    else setJugadoresA([]);
  }, [eqA]);

  useEffect(() => {
    if (eqB)
      get<Jugador[]>(`/equipos/${eqB.id}/jugadores`)
        .then(setJugadoresB)
        .catch(() => setJugadoresB([]));
    else setJugadoresB([]);
  }, [eqB]);

  useEffect(() => {
    if (modo === "equipos") {
      if (!eqA || !eqB) return;
      setCargando(true);
      Promise.all([
        get<Stat[]>(`/equipos/${eqA.id}/estadisticas`),
        get<Stat[]>(`/equipos/${eqB.id}/estadisticas`),
      ])
        .then(([a, b]) => {
          setStatsA(a);
          setStatsB(b);
        })
        .catch(() => {})
        .finally(() => setCargando(false));
    } else {
      if (!jugA || !jugB) return;
      setCargando(true);
      Promise.all([
        get<Stat[]>(`/jugadores/${jugA.id}/estadisticas`),
        get<Stat[]>(`/jugadores/${jugB.id}/estadisticas`),
      ])
        .then(([a, b]) => {
          setStatsA(a);
          setStatsB(b);
        })
        .catch(() => {})
        .finally(() => setCargando(false));
    }
  }, [modo, eqA, eqB, jugA, jugB]);

  const rows = buildRows(statsA, statsB);
  const filas = displayRows(rows, modo);
  const nombreA = modo === "equipos" ? eqA?.nombre : jugA?.nombre;
  const nombreB = modo === "equipos" ? eqB?.nombre : jugB?.nombre;
  const ambosSeleccionados = modo === "equipos" ? !!(eqA && eqB) : !!(jugA && jugB);
  const showResults = ambosSeleccionados && rows.length > 0;

  return (
    <div className="space-y-5">
      <div className="flex gap-2">
        {(["equipos", "jugadores"] as const).map((m) => (
          <button
            key={m}
            onClick={() => {
              setModo(m);
              setStatsA([]);
              setStatsB([]);
            }}
            className={`navlink capitalize ${modo === m ? "navlink-active" : ""}`}
          >
            {m}
          </button>
        ))}
      </div>

      {modo === "equipos" ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <CompareSelector
            items={equipos.filter((e) => e.id !== eqB?.id)}
            value={eqA}
            onChange={setEqA}
            label="Equipo A"
            getKey={(e) => e.id}
            getLabel={(e) => e.nombre}
          />
          <CompareSelector
            items={equipos.filter((e) => e.id !== eqA?.id)}
            value={eqB}
            onChange={setEqB}
            label="Equipo B"
            getKey={(e) => e.id}
            getLabel={(e) => e.nombre}
          />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <CompareSelector
              items={equipos}
              value={eqA}
              onChange={(e) => {
                setEqA(e);
                setJugA(null);
              }}
              label="Equipo del jugador A"
              getKey={(e) => e.id}
              getLabel={(e) => e.nombre}
            />
            {jugadoresA.length > 0 && (
              <CompareSelector
                items={jugadoresA.filter((j) => j.id !== jugB?.id)}
                value={jugA}
                onChange={setJugA}
                label="Jugador A"
                getKey={(j) => j.id}
                getLabel={(j) => `${j.nombre} (${j.posicion})`}
              />
            )}
          </div>
          <div className="space-y-2">
            <CompareSelector
              items={equipos}
              value={eqB}
              onChange={(e) => {
                setEqB(e);
                setJugB(null);
              }}
              label="Equipo del jugador B"
              getKey={(e) => e.id}
              getLabel={(e) => e.nombre}
            />
            {jugadoresB.length > 0 && (
              <CompareSelector
                items={jugadoresB.filter((j) => j.id !== jugA?.id)}
                value={jugB}
                onChange={setJugB}
                label="Jugador B"
                getKey={(j) => j.id}
                getLabel={(j) => `${j.nombre} (${j.posicion})`}
              />
            )}
          </div>
        </div>
      )}

      {!ambosSeleccionados && !cargando && (
        <p className="text-sm text-muted">
          {modo === "equipos"
            ? "Elegí dos equipos para comparar."
            : "Elegí el equipo de cada jugador y luego el jugador."}
        </p>
      )}

      {ambosSeleccionados && !cargando && rows.length === 0 && (
        <p className="text-sm text-muted">Sin estadísticas disponibles para estos {modo}.</p>
      )}

      {cargando && <p className="text-sm text-muted">Cargando estadísticas…</p>}

      {showResults && !cargando && (
        <div className="space-y-4">
          {rows.length > 2 && (
            <div className="card">
              <h2 className="mb-3 text-sm font-semibold text-muted">Radar</h2>
              <StatsRadar rows={rows} nombreA={nombreA!} nombreB={nombreB!} />
            </div>
          )}

          <div className="card overflow-x-auto">
            <div className="mb-3 grid grid-cols-3 text-xs font-semibold uppercase tracking-wide text-muted">
              <span>
                {modo === "equipos" && eqA && (
                  <span className="flex items-center gap-2">
                    <Crest src={eqA.escudo} size={16} />
                    {eqA.nombre}
                  </span>
                )}
                {modo === "jugadores" && <span>{jugA?.nombre}</span>}
              </span>
              <span className="text-center">Métrica</span>
              <span className="text-right">
                {modo === "equipos" && eqB && (
                  <span className="flex items-center justify-end gap-2">
                    {eqB.nombre}
                    <Crest src={eqB.escudo} size={16} />
                  </span>
                )}
                {modo === "jugadores" && <span>{jugB?.nombre}</span>}
              </span>
            </div>
            <table className="w-full text-sm">
              <tbody>
                {filas.map((row) => {
                  const aNum = row.a ?? 0;
                  const bNum = row.b ?? 0;
                  return (
                    <tr key={row.metrica} className="border-t border-line">
                      <td className="py-2 tabular-nums">
                        <span
                          className={
                            aNum > bNum ? "font-bold text-accent" : "text-muted"
                          }
                        >
                          {row.a ?? "—"}
                        </span>
                      </td>
                      <td className="py-2 text-center text-xs text-muted">
                        {row.metrica}
                      </td>
                      <td className="py-2 text-right tabular-nums">
                        <span
                          className={
                            bNum > aNum ? "font-bold text-accent" : "text-muted"
                          }
                        >
                          {row.b ?? "—"}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
