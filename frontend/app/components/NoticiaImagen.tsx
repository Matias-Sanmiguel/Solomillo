/* eslint-disable @next/next/no-img-element */
import { NoticiaImagen as ImagenData } from "@/lib/api";
import { Crest } from "./Crest";

/**
 * Compone la "imagen" de una noticia sin IA generativa: fondo de estadio (CSS),
 * escudos/banderas grandes y marcador. Variantes según el tipo de noticia.
 */
export function NoticiaImagen({
  data,
  alto = 200,
  destacada = false,
}: {
  data: ImagenData;
  alto?: number;
  destacada?: boolean;
}) {
  const crestSize = destacada ? 116 : 76;

  return (
    <div
      className="relative w-full overflow-hidden rounded-xl border border-line"
      style={{ height: alto, background: ESTADIO_BG }}
    >
      {/* Líneas de cancha sutiles */}
      <div className="pointer-events-none absolute inset-0 opacity-30" style={{ background: CANCHA }} />
      {/* Círculo central */}
      <div
        className="pointer-events-none absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full border border-white/15"
        style={{ width: alto * 0.7, height: alto * 0.7 }}
      />

      <div className="relative flex h-full items-center justify-center gap-6 px-6">
        {contenido(data, crestSize, destacada)}
      </div>
    </div>
  );
}

function contenido(data: ImagenData, crestSize: number, destacada: boolean) {
  if (data.tipo === "PARTIDO" && data.local && data.visitante) {
    return (
      <>
        <Lado nombre={data.local.nombre} escudo={data.local.escudo} size={crestSize} />
        <div className="flex flex-col items-center">
          {data.marcador ? (
            <div className={`font-black tabular-nums tracking-tight ${destacada ? "text-5xl" : "text-3xl"}`}>
              {data.marcador.replace("-", " : ")}
            </div>
          ) : (
            <div className={`font-black text-muted ${destacada ? "text-3xl" : "text-xl"}`}>VS</div>
          )}
          <span className="mt-1 text-[10px] uppercase tracking-widest text-white/50">Mundial 2026</span>
        </div>
        <Lado nombre={data.visitante.nombre} escudo={data.visitante.escudo} size={crestSize} />
      </>
    );
  }

  if (data.tipo === "JUGADOR" && data.jugador) {
    return (
      <div className="flex flex-col items-center text-center">
        <Crest src={data.jugador.escudo} size={crestSize} />
        <div className={`mt-2 font-bold ${destacada ? "text-2xl" : "text-lg"}`}>{data.jugador.nombre}</div>
        {data.marcador ? (
          <div className="mt-0.5 text-sm tabular-nums text-white/70">{data.marcador.replace("-", " : ")}</div>
        ) : null}
      </div>
    );
  }

  const equipo = data.local ?? data.visitante;
  return (
    <div className="flex flex-col items-center text-center">
      <Crest src={equipo?.escudo} size={crestSize} />
      {equipo ? <div className={`mt-2 font-bold ${destacada ? "text-2xl" : "text-lg"}`}>{equipo.nombre}</div> : null}
    </div>
  );
}

function Lado({ nombre, escudo, size }: { nombre: string; escudo: string; size: number }) {
  return (
    <div className="flex flex-col items-center text-center">
      <Crest src={escudo} size={size} />
      <div className="mt-2 max-w-[110px] truncate text-sm font-semibold">{nombre}</div>
    </div>
  );
}

const ESTADIO_BG =
  "radial-gradient(120% 80% at 50% 0%, #1f3b2e 0%, #14253a 55%, #0a0e1a 100%)";
const CANCHA =
  "repeating-linear-gradient(90deg, rgba(255,255,255,0.06) 0px, rgba(255,255,255,0.06) 2px, transparent 2px, transparent 48px)";
