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

function normalize(rows: StatRow[]): { metrica: string; a: number; b: number }[] {
  return rows.map((r) => {
    const max = Math.max(r.a ?? 0, r.b ?? 0, 1);
    return {
      metrica: r.metrica.replace(/_/g, " "),
      a: Math.round(((r.a ?? 0) / max) * 100),
      b: Math.round(((r.b ?? 0) / max) * 100),
    };
  });
}

export function StatsRadar({ rows, nombreA, nombreB }: Props) {
  const data = normalize(rows);

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
