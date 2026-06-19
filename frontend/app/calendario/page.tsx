"use client";
import { useEffect, useMemo, useState } from "react";
import { get, Equipo, Partido } from "@/lib/api";
import { Crest } from "../components/Crest";

type Torneo = { id: number; nombre: string; temporada: string };

const RONDA_LABEL: Record<string, string> = {
  GRUPOS: "Fase de grupos",
  DIECISEISAVOS: "Dieciseisavos de final",
  OCTAVOS: "Octavos de final",
  CUARTOS: "Cuartos de final",
  SEMIFINAL: "Semifinales",
  TERCER_PUESTO: "Tercer puesto",
  FINAL: "Final",
};

const diaLabel = (iso: string) =>
  new Date(iso).toLocaleDateString("es-AR", {
    weekday: "long",
    day: "2-digit",
    month: "long",
  });

const horaLabel = (iso: string) =>
  new Date(iso).toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
  });

function Lado({
  equipo,
  right,
}: {
  equipo?: Equipo;
  right?: boolean;
}) {
  return (
    <div
      className={`flex flex-1 items-center gap-2 ${right ? "flex-row-reverse text-right" : ""}`}
    >
      <Crest src={equipo?.escudo} size={26} />
      <span className="truncate text-sm font-semibold">
        {equipo?.nombre ?? "Por definir"}
      </span>
    </div>
  );
}

function Centro({ p }: { p: Partido }) {
  if (p.estado === "FINALIZADO")
    return (
      <div className="px-3 text-center">
        <div className="text-[10px] font-semibold uppercase tracking-wide text-muted">
          Final
        </div>
        <div className="text-2xl font-bold tabular-nums">
          {p.goles_local}
          <span className="mx-1 text-muted">-</span>
          {p.goles_visitante}
        </div>
      </div>
    );
  if (p.estado === "EN_VIVO")
    return (
      <div className="px-3 text-center">
        <div className="flex items-center justify-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-good">
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-good opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-good" />
          </span>
          En vivo
        </div>
        <div className="text-2xl font-bold tabular-nums">
          {p.goles_local}
          <span className="mx-1 text-muted">-</span>
          {p.goles_visitante}
        </div>
      </div>
    );
  return (
    <div className="px-3 text-center">
      <div className="rounded-lg border border-line px-2 py-1 text-xs font-semibold tabular-nums">
        {p.fecha_hora ? horaLabel(p.fecha_hora) : "—"}
      </div>
    </div>
  );
}

export default function CalendarioPage() {
  const [partidos, setPartidos] = useState<Partido[]>([]);
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    Promise.all([
      get<Torneo[]>("/torneos"),
      get<Partido[]>("/partidos"),
      get<Equipo[]>("/equipos"),
    ])
      .then(([torneos, ps, es]) => {
        const mundial = torneos.find((t) =>
          t.nombre.includes("Copa Mundial FIFA 2026"),
        );
        setPartidos(
          mundial ? ps.filter((p) => p.torneo_id === mundial.id) : ps,
        );
        setEquipos(es);
      })
      .catch(() => {})
      .finally(() => setCargando(false));
  }, []);

  const eq = useMemo(() => {
    const m = new Map<number, Equipo>();
    equipos.forEach((e) => m.set(e.id, e));
    return m;
  }, [equipos]);

  // Agrupa por día respetando el orden cronológico.
  const porDia = useMemo(() => {
    const sorted = [...partidos].sort((a, b) =>
      (a.fecha_hora ?? "").localeCompare(b.fecha_hora ?? ""),
    );
    const grupos: { dia: string; items: Partido[] }[] = [];
    for (const p of sorted) {
      const dia = p.fecha_hora ? p.fecha_hora.slice(0, 10) : "—";
      const last = grupos[grupos.length - 1];
      if (last && last.dia === dia) last.items.push(p);
      else grupos.push({ dia, items: [p] });
    }
    return grupos;
  }, [partidos]);

  if (cargando) return <p className="text-muted">Cargando calendario…</p>;
  if (partidos.length === 0)
    return <p className="text-muted">No hay partidos cargados.</p>;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold">Calendario</h2>
        <span className="chip">Mundial 2026</span>
      </div>

      {porDia.map(({ dia, items }) => (
        <div key={dia}>
          <h3 className="mb-3 text-sm font-semibold capitalize text-muted">
            {dia !== "—" ? diaLabel(items[0].fecha_hora!) : "Sin fecha"}
          </h3>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {items.map((p) => (
              <div key={p.id} className="card flex flex-col gap-2">
                <div className="flex items-center justify-between text-[11px] text-muted">
                  <span>
                    {p.ronda === "GRUPOS"
                      ? `Grupo ${p.grupo ?? ""}`
                      : (RONDA_LABEL[p.ronda ?? ""] ?? "")}
                  </span>
                  <span className="truncate pl-2">{p.estadio}</span>
                </div>
                <div className="flex items-center justify-between gap-1">
                  <Lado equipo={p.local_id ? eq.get(p.local_id) : undefined} />
                  <Centro p={p} />
                  <Lado
                    equipo={p.visitante_id ? eq.get(p.visitante_id) : undefined}
                    right
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
