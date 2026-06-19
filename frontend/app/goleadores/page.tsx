"use client";

import { useEffect, useState } from "react";
import { get, Goleador, Torneo } from "@/lib/api";
import { Crest } from "../components/Crest";

export default function GoleadoresPage() {
  const [torneos, setTorneos] = useState<Torneo[]>([]);
  const [torneoId, setTorneoId] = useState<number | null>(null);
  const [tabla, setTabla] = useState<Goleador[]>([]);

  useEffect(() => {
    get<Torneo[]>("/torneos")
      .then((ts) => {
        setTorneos(ts);
        if (ts[0]) setTorneoId(ts[0].id);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (torneoId == null) return;
    get<Goleador[]>(`/torneos/${torneoId}/goleadores`)
      .then(setTabla)
      .catch(() => setTabla([]));
  }, [torneoId]);

  return (
    <div className="card overflow-x-auto">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold">Tabla de goleadores</h2>
        <select
          className="rounded-lg border border-line bg-panel2/60 px-3 py-1.5 text-sm"
          value={torneoId ?? ""}
          onChange={(e) => setTorneoId(Number(e.target.value))}
        >
          {torneos.map((t) => (
            <option key={t.id} value={t.id}>
              {t.nombre}
            </option>
          ))}
        </select>
      </div>

      <table className="w-full text-sm">
        <thead className="text-left text-xs uppercase text-muted">
          <tr>
            <th className="py-2">#</th>
            <th>Jugador</th>
            <th>Selección</th>
            <th className="text-right">Goles</th>
          </tr>
        </thead>
        <tbody>
          {tabla.map((g) => (
            <tr key={g.jugador_id} className="border-t border-line">
              <td className="py-2 text-muted tabular-nums">{g.posicion}</td>
              <td className="font-medium">{g.nombre}</td>
              <td>
                <span className="flex items-center gap-2">
                  <Crest src={g.escudo} size={20} />
                  <span className="text-muted">{g.equipo}</span>
                </span>
              </td>
              <td className="text-right font-semibold tabular-nums">{g.goles}</td>
            </tr>
          ))}
          {tabla.length === 0 && (
            <tr>
              <td colSpan={4} className="py-4 text-center text-muted">
                Sin goles registrados en este torneo.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
