"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Calibracion, Metricas } from "@/lib/api";

const tooltip = {
  background: "#121829",
  border: "1px solid #252e44",
  borderRadius: 12,
  fontSize: 12,
};

export function AccuracyLine({ serie }: { serie: Metricas["serie"] }) {
  if (!serie || serie.length === 0)
    return <p className="text-sm text-muted">Sin predicciones resueltas todavía.</p>;
  const data = serie.map((s) => ({ i: s.i, accuracy: Math.round(s.accuracy * 1000) / 10 }));
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
        <CartesianGrid stroke="#252e44" strokeDasharray="3 3" />
        <XAxis dataKey="i" stroke="#8a97b4" fontSize={11} tickLine={false} />
        <YAxis stroke="#8a97b4" fontSize={11} tickLine={false} domain={[0, 100]} unit="%" />
        <Tooltip contentStyle={tooltip} formatter={(v) => [`${v}%`, "accuracy"]} />
        <Line
          type="monotone"
          dataKey="accuracy"
          stroke="#34d399"
          strokeWidth={2}
          dot={false}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function CalibrationChart({ cal }: { cal: Calibracion | null }) {
  if (!cal || cal.bins.length === 0)
    return <p className="text-sm text-muted">Sin datos de calibración todavía.</p>;
  const data = cal.bins.map((b) => ({
    confianza: Math.round(b.confianza * 100),
    precision: Math.round(b.precision * 100),
  }));
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -18 }}>
        <CartesianGrid stroke="#252e44" strokeDasharray="3 3" />
        <XAxis
          dataKey="confianza"
          type="number"
          domain={[0, 100]}
          stroke="#8a97b4"
          fontSize={11}
          tickLine={false}
          unit="%"
        />
        <YAxis domain={[0, 100]} stroke="#8a97b4" fontSize={11} tickLine={false} unit="%" />
        <Tooltip contentStyle={tooltip} />
        <ReferenceLine
          segment={[
            { x: 0, y: 0 },
            { x: 100, y: 100 },
          ]}
          stroke="#475569"
          strokeDasharray="4 4"
        />
        <Line
          type="monotone"
          dataKey="precision"
          stroke="#38bdf8"
          strokeWidth={2}
          dot={{ r: 3, fill: "#38bdf8" }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
