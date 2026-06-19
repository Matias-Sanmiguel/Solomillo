"use client";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { WS, get, Partido } from "@/lib/api";

export type AlertaItem = {
  id: string;
  partido_id: number;
  tipo: string;
  mensaje: string;
  ts: number;
};

type AlertasCtx = {
  alertas: AlertaItem[];
  toasts: AlertaItem[];
  noLeidas: number;
  marcarLeidas: () => void;
  seguidos: number[];
  seguir: (id: number) => void;
  dejarDeSeguir: (id: number) => void;
  sigueA: (id: number) => boolean;
};

const Ctx = createContext<AlertasCtx | null>(null);
const LS_KEY = "solomillo:seguidos";

function loadSeguidos(): number[] {
  if (typeof window === "undefined") return [];
  try {
    return JSON.parse(localStorage.getItem(LS_KEY) ?? "[]");
  } catch {
    return [];
  }
}

export function AlertProvider({ children }: { children: React.ReactNode }) {
  const [alertas, setAlertas] = useState<AlertaItem[]>([]);
  const [toasts, setToasts] = useState<AlertaItem[]>([]);
  const [noLeidas, setNoLeidas] = useState(0);
  const [seguidos, setSeguidos] = useState<number[]>([]);
  const seguidosRef = useRef<number[]>([]);
  const partidoMapRef = useRef<Map<number, Partido>>(new Map());

  useEffect(() => {
    const s = loadSeguidos();
    setSeguidos(s);
    seguidosRef.current = s;
    get<Partido[]>("/partidos")
      .then((ps) => {
        const m = new Map<number, Partido>();
        ps.forEach((p) => m.set(p.id, p));
        partidoMapRef.current = m;
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    let ws: WebSocket | null = null;
    try {
      ws = new WebSocket(WS);
      ws.onmessage = (ev) => {
        try {
          const data = JSON.parse(ev.data as string);
          if (!data.mensaje) return;
          const partido = partidoMapRef.current.get(data.partido_id);
          const involucrados: number[] = partido
            ? [partido.local_id, partido.visitante_id]
            : [];
          const current = seguidosRef.current;
          const mostrar =
            current.length === 0 ||
            involucrados.some((id) => current.includes(id));
          if (!mostrar) return;
          const item: AlertaItem = {
            id: `${Date.now()}-${Math.random()}`,
            partido_id: data.partido_id ?? 0,
            tipo: data.tipo ?? "evento",
            mensaje: data.mensaje,
            ts: Date.now(),
          };
          setAlertas((prev) => [item, ...prev].slice(0, 50));
          setNoLeidas((n) => n + 1);
          setToasts((prev) => [...prev, item]);
          setTimeout(
            () => setToasts((prev) => prev.filter((t) => t.id !== item.id)),
            5000
          );
          if (
            typeof Notification !== "undefined" &&
            Notification.permission === "granted"
          ) {
            new Notification("Solomillo · " + item.tipo, {
              body: item.mensaje,
            });
          }
        } catch {
          /* ignore */
        }
      };
    } catch {
      /* ws optional */
    }
    return () => ws?.close();
  }, []);

  const marcarLeidas = useCallback(() => setNoLeidas(0), []);

  const seguir = useCallback((id: number) => {
    setSeguidos((prev) => {
      if (prev.includes(id)) return prev;
      const next = [...prev, id];
      localStorage.setItem(LS_KEY, JSON.stringify(next));
      seguidosRef.current = next;
      return next;
    });
  }, []);

  const dejarDeSeguir = useCallback((id: number) => {
    setSeguidos((prev) => {
      const next = prev.filter((x) => x !== id);
      localStorage.setItem(LS_KEY, JSON.stringify(next));
      seguidosRef.current = next;
      return next;
    });
  }, []);

  const sigueA = useCallback(
    (id: number) => seguidos.includes(id),
    [seguidos]
  );

  return (
    <Ctx.Provider
      value={{
        alertas,
        toasts,
        noLeidas,
        marcarLeidas,
        seguidos,
        seguir,
        dejarDeSeguir,
        sigueA,
      }}
    >
      {children}
    </Ctx.Provider>
  );
}

export function useAlertas(): AlertasCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAlertas must be inside AlertProvider");
  return ctx;
}
