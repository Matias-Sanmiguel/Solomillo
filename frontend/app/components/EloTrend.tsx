"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { EloPunto } from "@/lib/api";

type Serie = { nombre: string; color: string; data: EloPunto[] };

export function EloTrend({ series, height = 240 }: { series: Serie[]; height?: number }) {
  const len = Math.max(0, ...series.map((s) => s.data.length));
  if (len === 0) return <p className="text-sm text-muted">Sin historial de Elo todavía.</p>;

  const rows = Array.from({ length: len }, (_, i) => {
    const row: Record<string, number> = { i: i + 1 };
    series.forEach((s) => {
      const pt = s.data[i];
      if (pt) row[s.nombre] = Math.round(pt.elo);
    });
    return row;
  });

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid stroke="#252e44" strokeDasharray="3 3" />
        <XAxis dataKey="i" stroke="#8a97b4" fontSize={11} tickLine={false} />
        <YAxis stroke="#8a97b4" fontSize={11} tickLine={false} domain={["auto", "auto"]} />
        <Tooltip
          contentStyle={{
            background: "#121829",
            border: "1px solid #252e44",
            borderRadius: 12,
            fontSize: 12,
          }}
        />
        {series.map((s) => (
          <Line
            key={s.nombre}
            type="monotone"
            dataKey={s.nombre}
            stroke={s.color}
            strokeWidth={2}
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  );
}
