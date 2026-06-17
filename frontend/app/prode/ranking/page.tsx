"use client";

import { useEffect, useState } from "react";
import { get, getToken, RankingProde } from "@/lib/api";

export default function RankingProdePage() {
  const [ranking, setRanking] = useState<RankingProde[]>([]);
  const [yo, setYo] = useState<RankingProde | null>(null);

  useEffect(() => {
    get<RankingProde[]>("/prode/ranking").then(setRanking).catch(() => {});
    if (getToken()) {
      get<RankingProde>("/prode/ranking/me")
        .then((r) => setYo(r.posicion ? r : null))
        .catch(() => {});
    }
  }, []);

  return (
    <div className="card max-w-2xl overflow-x-auto">
      <h2 className="mb-3 text-lg font-semibold">Ranking del Prode</h2>
      <table className="w-full text-sm">
        <thead className="text-left text-xs uppercase text-muted">
          <tr>
            <th className="py-2">#</th>
            <th>Usuario</th>
            <th className="text-right">Puntos</th>
            <th className="text-right">Aciertos</th>
            <th className="text-right">Jugados</th>
          </tr>
        </thead>
        <tbody>
          {ranking.map((r) => (
            <tr
              key={r.usuario_id}
              className={`border-t border-line ${
                yo?.usuario_id === r.usuario_id ? "bg-panel2/80" : ""
              }`}
            >
              <td className="py-2 text-muted tabular-nums">{r.posicion}</td>
              <td className="font-medium">{r.nombre}</td>
              <td className="text-right font-semibold tabular-nums">{r.puntos}</td>
              <td className="text-right text-muted tabular-nums">{r.aciertos}</td>
              <td className="text-right text-muted tabular-nums">{r.pronosticos}</td>
            </tr>
          ))}
          {ranking.length === 0 && (
            <tr>
              <td colSpan={5} className="py-4 text-center text-muted">
                Todavía no hay puntajes. ¡Cargá tus pronósticos!
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {yo && !ranking.some((r) => r.usuario_id === yo.usuario_id) && (
        <p className="mt-3 text-xs text-muted">
          Tu posición: <b>#{yo.posicion}</b> · {yo.puntos} pts
        </p>
      )}
    </div>
  );
}
