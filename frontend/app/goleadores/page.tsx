"use client";
import { useEffect, useState } from "react";
import { get } from "@/lib/api";
import { Crest } from "../components/Crest";

type Jugador = {
  jugador_id: number;
  nombre: string;
  posicion: string;
  equipo: string;
  escudo: string;
  valor: number;
};

type Data = { goleadores: Jugador[]; asistentes: Jugador[] };

function Lista({
  titulo,
  unidad,
  jugadores,
}: {
  titulo: string;
  unidad: string;
  jugadores: Jugador[];
}) {
  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold">{titulo}</h2>
        <span className="chip">{jugadores.length} jugadores</span>
      </div>
      {jugadores.length === 0 ? (
        <p className="text-sm text-muted">Sin datos todavía.</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {jugadores.map((j, i) => (
            <div
              key={j.jugador_id}
              className="card flex items-center gap-3 py-3"
            >
              <span className="w-6 text-center text-lg font-bold tabular-nums text-muted">
                {i + 1}
              </span>
              <Crest src={j.escudo} size={28} />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{j.nombre}</div>
                <div className="truncate text-xs text-muted">
                  {j.equipo}
                  {j.posicion ? ` · ${j.posicion}` : ""}
                </div>
              </div>
              <div className="text-right">
                <div className="text-xl font-bold tabular-nums text-accent">
                  {j.valor}
                </div>
                <div className="text-[10px] uppercase tracking-wide text-muted">
                  {unidad}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function GoleadoresPage() {
  const [data, setData] = useState<Data>({ goleadores: [], asistentes: [] });
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    get<Data>("/goleadores")
      .then(setData)
      .catch(() => {})
      .finally(() => setCargando(false));
  }, []);

  if (cargando) return <p className="text-muted">Cargando goleadores…</p>;

  return (
    <div className="space-y-8">
      <Lista
        titulo="Máximos goleadores"
        unidad="goles"
        jugadores={data.goleadores}
      />
      <Lista
        titulo="Máximos asistentes"
        unidad="asist."
        jugadores={data.asistentes}
      />
    </div>
  );
}
