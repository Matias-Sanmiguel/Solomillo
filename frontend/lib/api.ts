export const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";
export const WS = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8000/ws/eventos";

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API}${path}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return res.json();
}

export type Equipo = {
  id: number;
  nombre: string;
  escudo: string;
  entrenador?: string;
  estadio?: string;
};

export type Partido = {
  id: number;
  torneo_id: number;
  local_id: number;
  visitante_id: number;
  fecha_hora: string | null;
  estadio: string;
  estado: "PROGRAMADO" | "EN_VIVO" | "FINALIZADO";
  goles_local: number | null;
  goles_visitante: number | null;
};

export type Probabilidades = { local: number; empate: number; visitante: number };

export type Prediccion = {
  partido_id: number;
  modelo_version: number;
  probabilidades: Probabilidades;
};

export type Modelo = {
  version: number;
  tipo: string;
  accuracy: number;
  log_loss: number;
  brier: number;
  activo: boolean;
};

export type Metricas = {
  version: number;
  n: number;
  accuracy?: number;
  log_loss?: number;
  brier?: number;
  serie?: { i: number; accuracy: number }[];
};

export type Calibracion = {
  version: number;
  bins: { confianza: number; precision: number; n: number }[];
};

export type EloEquipo = {
  equipo_id: number;
  nombre: string;
  escudo: string;
  elo: number;
  puntos_fifa: number;
};

export type EloPunto = { fecha: string; elo: number };

export const pct = (v: number) => `${Math.round(v * 100)}%`;

export const fmtFecha = (iso: string | null) =>
  iso
    ? new Date(iso).toLocaleString("es-AR", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "—";
