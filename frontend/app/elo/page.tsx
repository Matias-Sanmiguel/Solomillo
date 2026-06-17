"use client";

import { useEffect, useState } from "react";
import { get, EloEquipo, EloPunto } from "@/lib/api";
import { Crest } from "../components/Crest";
import { EloTrend } from "../components/EloTrend";

export default function EloPage() {
  const [ranking, setRanking] = useState<EloEquipo[]>([]);
  const [sel, setSel] = useState<EloEquipo | null>(null);
  const [hist, setHist] = useState<EloPunto[]>([]);

  useEffect(() => {
    get<EloEquipo[]>("/ml/elo")
      .then((r) => {
        setRanking(r);
        if (r[0]) elegir(r[0]);
      })
      .catch(() => {});
  }, []);

  const elegir = (e: EloEquipo) => {
    setSel(e);
    get<EloPunto[]>(`/ml/elo/${e.equipo_id}/historial`).then(setHist).catch(() => setHist([]));
  };

  return (
    <div className="grid gap-4 lg:grid-cols-[1.2fr_1fr]">
      <div className="card overflow-x-auto">
        <h2 className="mb-3 text-lg font-semibold">Ranking Elo</h2>
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-muted">
            <tr>
              <th className="py-2">#</th>
              <th>Selección</th>
              <th className="text-right">Elo</th>
              <th className="text-right">FIFA</th>
            </tr>
          </thead>
          <tbody>
            {ranking.map((e, i) => (
              <tr
                key={e.equipo_id}
                onClick={() => elegir(e)}
                className={`cursor-pointer border-t border-line transition hover:bg-panel2/60 ${
                  sel?.equipo_id === e.equipo_id ? "bg-panel2/80" : ""
                }`}
              >
                <td className="py-2 text-muted tabular-nums">{i + 1}</td>
                <td>
                  <span className="flex items-center gap-2">
                    <Crest src={e.escudo} size={20} />
                    <span className="font-medium">{e.nombre}</span>
                  </span>
                </td>
                <td className="text-right font-semibold tabular-nums">{Math.round(e.elo)}</td>
                <td className="text-right text-muted tabular-nums">{e.puntos_fifa || "—"}</td>
              </tr>
            ))}
            {ranking.length === 0 && (
              <tr>
                <td colSpan={4} className="py-4 text-center text-muted">
                  Sin datos de Elo.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2 className="mb-1 text-lg font-semibold">{sel ? sel.nombre : "Evolución"}</h2>
        <p className="mb-3 text-xs text-muted">Elo tras cada partido finalizado.</p>
        {sel ? (
          <EloTrend series={[{ nombre: sel.nombre, color: "#38bdf8", data: hist }]} height={300} />
        ) : (
          <p className="text-sm text-muted">Elegí una selección.</p>
        )}
      </div>
    </div>
  );
}
