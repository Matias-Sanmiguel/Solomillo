export const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";
export const WS = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8000/ws/eventos";

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API}${path}`, { cache: "no-store" });
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return res.json();
}

export type Torneo = { id: number; nombre: string; temporada: string };
export type Equipo = { id: number; nombre: string; entrenador: string; escudo: string; estadio: string };
export type Jugador = { id: number; nombre: string; posicion: string; numero: number };
export type Partido = {
  id: number;
  torneo_id: number;
  local_id: number;
  visitante_id: number;
  fecha_hora: string;
  estadio: string;
};
export type Posicion = { equipo_id: number; puntos: number; gf: number; gc: number; dif: number };
export type Proyeccion = {
  equipo_id: number;
  puntos_actuales: number;
  puntos_esperados: number;
  posicion_proyectada: number;
};
export type StatJugador = { metrica: string; valor: number; torneo_id: number };
export type FeedItem = { id: number; texto: string; tag: string };

export const fmtFecha = (iso: string) =>
  new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
