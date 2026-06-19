"use client";
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  ResponsiveContainer,
  Legend,
} from "recharts";

type StatRow = { metrica: string; a: number | null; b: number | null };

type Props = {
  rows: StatRow[];
  nombreA: string;
  nombreB: string;
};

type Lado = "a" | "b";

function valor(rows: StatRow[], metrica: string, lado: Lado): number {
  const r = rows.find((x) => x.metrica === metrica);
  return (r?.[lado] ?? 0) as number;
}

const clamp = (n: number) => Math.max(0, Math.min(100, Math.round(n)));

/**
 * Cinco dimensiones "futboleras" derivadas de las métricas crudas, escaladas a 0–100
 * con topes absolutos razonables (no relativos entre los dos equipos) para que el radar
 * tenga lectura propia: más área = mejor en todas las dimensiones.
 */
function dimensiones(rows: StatRow[], lado: Lado) {
  const pj = Math.max(1, valor(rows, "partidos_jugados", lado));
  const gf = valor(rows, "goles_favor", lado) / pj;
  const gc = valor(rows, "goles_contra", lado) / pj;
  const tarjetas =
    (valor(rows, "tarjetas_amarillas", lado) +
      2 * valor(rows, "tarjetas_rojas", lado)) /
    pj;
  const vict = valor(rows, "victorias", lado) / pj;
  const pts = valor(rows, "puntos", lado) / pj;

  return {
    Ataque: clamp((gf / 3) * 100), // 3 goles/partido = tope
    Defensa: clamp(100 - (gc / 3) * 100), // 0 goles recibidos = 100
    Disciplina: clamp(100 - (tarjetas / 4) * 100), // 4 tarjetas pond./partido = peor
    Efectividad: clamp(vict * 100), // % de victorias
    Rendimiento: clamp((pts / 3) * 100), // puntos por partido sobre 3
  };
}

const EJES = ["Ataque", "Defensa", "Disciplina", "Efectividad", "Rendimiento"] as const;

/** Radar crudo (fallback p. ej. para jugadores): normaliza cada métrica al máximo del par. */
function radarCrudo(rows: StatRow[]) {
  return rows.slice(0, 8).map((r) => {
    const max = Math.max(r.a ?? 0, r.b ?? 0, 1);
    return {
      metrica: r.metrica.replace(/_/g, " "),
      a: Math.round(((r.a ?? 0) / max) * 100),
      b: Math.round(((r.b ?? 0) / max) * 100),
    };
  });
}

export function StatsRadar({ rows, nombreA, nombreB }: Props) {
  // Si hay métricas de resultado de equipo, usamos las 5 dimensiones derivadas.
  const esEquipo = rows.some((r) => r.metrica === "partidos_jugados");

  const data = esEquipo
    ? (() => {
        const dimA = dimensiones(rows, "a");
        const dimB = dimensiones(rows, "b");
        return EJES.map((eje) => ({ metrica: eje, a: dimA[eje], b: dimB[eje] }));
      })()
    : radarCrudo(rows);

  if (data.length < 3) {
    return (
      <p className="text-sm text-muted">
        No hay suficientes métricas para el radar.
      </p>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <RadarChart data={data}>
        <PolarGrid stroke="#252e44" />
        <PolarAngleAxis
          dataKey="metrica"
          tick={{ fill: "#8a97b4", fontSize: 11 }}
        />
        <Radar
          name={nombreA}
          dataKey="a"
          stroke="#22d3ee"
          fill="#22d3ee"
          fillOpacity={0.2}
        />
        <Radar
          name={nombreB}
          dataKey="b"
          stroke="#f472b6"
          fill="#f472b6"
          fillOpacity={0.2}
        />
        <Legend wrapperStyle={{ fontSize: 12, color: "#8a97b4" }} />
      </RadarChart>
    </ResponsiveContainer>
  );
}
