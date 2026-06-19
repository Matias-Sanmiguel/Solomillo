export const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";
export const WS = process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8000/ws/eventos";

const TOKEN_KEY = "solomillo_token";

export const getToken = () =>
  typeof window === "undefined" ? null : window.localStorage.getItem(TOKEN_KEY);
export const setToken = (t: string | null) => {
  if (typeof window === "undefined") return;
  if (t) window.localStorage.setItem(TOKEN_KEY, t);
  else window.localStorage.removeItem(TOKEN_KEY);
};

function authHeaders(): Record<string, string> {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
}

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API}${path}`, { cache: "no-store", headers: authHeaders() });
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return res.json();
}

export async function send<T>(method: string, path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    method,
    cache: "no-store",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${res.status} ${path}`);
  return res.json();
}

export async function login(email: string, password: string): Promise<string> {
  const r = await send<{ access_token: string }>("POST", "/auth/login", { email, password });
  setToken(r.access_token);
  return r.access_token;
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
  local_id: number | null;
  visitante_id: number | null;
  fecha_hora: string | null;
  estadio: string;
  grupo?: string | null;
  ronda?: string | null;
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

export type Torneo = {
  id: number;
  nombre: string;
  temporada: string;
};

export type Goleador = {
  posicion: number;
  jugador_id: number;
  nombre: string;
  equipo_id: number | null;
  equipo: string;
  escudo: string;
  goles: number;
};

export type EloEquipo = {
  equipo_id: number;
  nombre: string;
  escudo: string;
  elo: number;
  puntos_fifa: number;
};

export type EloPunto = { fecha: string; elo: number };

export type Signo = "LOCAL" | "EMPATE" | "VISITANTE";

export type ProdePronostico = {
  partido_id: number;
  signo: Signo | null;
  goles_local: number | null;
  goles_visitante: number | null;
  puntos: number | null;
};

export type ProdePartido = {
  partido_id: number;
  torneo_id: number;
  local_id: number;
  visitante_id: number;
  fecha_hora: string | null;
  mi_pronostico: ProdePronostico | null;
};

export type RankingProde = {
  posicion: number;
  usuario_id: number;
  nombre: string;
  puntos: number;
  aciertos: number;
  pronosticos: number;
};

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
