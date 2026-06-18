"use client";

type Jugador = {
  id: number;
  nombre: string;
  posicion: string;
  numero: number;
};

type Props = {
  local: Jugador[];
  visitante: Jugador[];
  nombreLocal: string;
  nombreVisitante: string;
};

type Linea = "GK" | "DEF" | "MID" | "FWD";

const KEYS: Record<Linea, string[]> = {
  GK: ["portero", "goalkeeper", "arquero", "gk", "goleiro"],
  DEF: ["defensa", "defender", "central", "lateral", "df", "cb", "lb", "rb", "back"],
  MID: ["mediocampista", "midfielder", "mf", "cm", "dm", "am", "medio", "volante", "centrocampista"],
  FWD: ["delantero", "forward", "fw", "st", "cf", "lw", "rw", "winger", "extremo", "atacante"],
};

function toLinea(posicion: string): Linea {
  const p = posicion.toLowerCase();
  for (const [l, keys] of Object.entries(KEYS) as [Linea, string[]][]) {
    if (keys.some((k) => p.includes(k))) return l;
  }
  return "MID";
}

function agrupar(jugadores: Jugador[]): Record<Linea, Jugador[]> {
  const grupos: Record<Linea, Jugador[]> = { GK: [], DEF: [], MID: [], FWD: [] };
  jugadores.forEach((j) => grupos[toLinea(j.posicion)].push(j));
  return grupos;
}

function PlayerChip({ jugador, color }: { jugador: Jugador; color: string }) {
  const short = jugador.nombre.split(" ").slice(-1)[0];
  return (
    <div className="flex flex-col items-center gap-0.5">
      <div
        className="flex h-8 w-8 items-center justify-center rounded-full border-2 text-xs font-bold text-white shadow"
        style={{ borderColor: color, background: "rgba(0,0,0,0.5)" }}
      >
        {jugador.numero || "?"}
      </div>
      <span className="max-w-[52px] truncate text-center text-[10px] text-white/80">
        {short}
      </span>
    </div>
  );
}

function TeamRows({
  jugadores,
  color,
  flip,
}: {
  jugadores: Jugador[];
  color: string;
  flip: boolean;
}) {
  const grupos = agrupar(jugadores);
  const orden: Linea[] = flip
    ? ["GK", "DEF", "MID", "FWD"]
    : ["FWD", "MID", "DEF", "GK"];

  return (
    <>
      {orden.map((l) =>
        grupos[l].length > 0 ? (
          <div key={l} className="flex items-center justify-center gap-3 py-2">
            {grupos[l].map((j) => (
              <PlayerChip key={j.id} jugador={j} color={color} />
            ))}
          </div>
        ) : null
      )}
    </>
  );
}

export function PitchFormation({
  local,
  visitante,
  nombreLocal,
  nombreVisitante,
}: Props) {
  if (local.length === 0 && visitante.length === 0) {
    return <p className="text-sm text-muted">Sin datos de plantel.</p>;
  }

  return (
    <div
      className="relative overflow-hidden rounded-xl border border-line"
      style={{ background: "#1a472a" }}
    >
      <div
        className="absolute inset-0 opacity-20"
        style={{
          backgroundImage:
            "repeating-linear-gradient(0deg, transparent, transparent 48px, rgba(255,255,255,0.08) 48px, rgba(255,255,255,0.08) 50px)",
        }}
      />
      <div
        className="absolute bottom-0 left-1/2 top-0 w-px -translate-x-1/2"
        style={{ background: "rgba(255,255,255,0.15)" }}
      />

      <div className="relative px-3 py-2">
        <p className="mb-1 text-center text-xs font-semibold text-white/70">
          {nombreLocal}
        </p>
        {local.length > 0 ? (
          <TeamRows jugadores={local} color="#22d3ee" flip={false} />
        ) : (
          <p className="py-4 text-center text-xs text-white/40">Sin plantel</p>
        )}
      </div>

      <div
        className="mx-4 border-t"
        style={{ borderColor: "rgba(255,255,255,0.15)" }}
      />

      <div className="relative px-3 py-2">
        {visitante.length > 0 ? (
          <TeamRows jugadores={visitante} color="#f472b6" flip={true} />
        ) : (
          <p className="py-4 text-center text-xs text-white/40">Sin plantel</p>
        )}
        <p className="mt-1 text-center text-xs font-semibold text-white/70">
          {nombreVisitante}
        </p>
      </div>
    </div>
  );
}
