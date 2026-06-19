# Alertas, Comparador y Formación Visual — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add in-page alert notifications for followed teams, a team/player comparison page, and a visual formation display on the match detail page.

**Architecture:** Pure frontend changes. All backend endpoints already exist. Alert system uses the existing WebSocket at `/ws/eventos` — messages with a `mensaje` field are alerts; we filter by followed teams stored in `localStorage`. Comparador is a new `/compare` route consuming existing stats endpoints. Formation reads `/equipos/{id}/jugadores` and groups players by position into a CSS pitch layout.

**Tech Stack:** Next.js 14, React 18, TypeScript, Tailwind CSS, Recharts 2.12.7

## Global Constraints

- No backend changes.
- No new npm packages — only what's in `package.json`.
- Follow existing Tailwind color tokens: `panel`, `panel2`, `line`, `muted`, `accent`, `good`, `warn`, `local`, `visit`, `ink`.
- No comments in code unless non-obvious.
- No Claude co-author in commits.
- Branch: `feature/alertas-comparador-formacion`.

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `frontend/lib/alertas.tsx` | Create | AlertContext, AlertProvider, useAlertas hook, seguidos localStorage logic |
| `frontend/app/components/Providers.tsx` | Create | "use client" wrapper that holds AlertProvider (needed since layout.tsx is a server component) |
| `frontend/app/components/BellMenu.tsx` | Create | Bell icon with badge + dropdown list of recent alerts |
| `frontend/app/components/SeguirEquipo.tsx` | Create | Toggle follow/unfollow button for a team |
| `frontend/app/layout.tsx` | Modify | Wrap children with Providers, add BellMenu to header |
| `frontend/app/components/Nav.tsx` | Modify | Add "Comparar" nav link |
| `frontend/app/page.tsx` | Modify | Add SeguirEquipo button to each match card |
| `frontend/app/compare/page.tsx` | Create | Comparador page — Equipos / Jugadores tabs |
| `frontend/app/components/CompareSelector.tsx` | Create | Searchable dropdown for selecting equipo or jugador |
| `frontend/app/components/StatsRadar.tsx` | Create | Recharts RadarChart for stat comparison |
| `frontend/app/components/PitchFormation.tsx` | Create | CSS football pitch with players grouped by position |
| `frontend/app/match/[id]/page.tsx` | Modify | Add formation card for both teams |

---

## Task 1: Alert Context (`lib/alertas.tsx`)

**Files:**
- Create: `frontend/lib/alertas.tsx`

**Interfaces:**
- Produces:
  - `AlertaItem = { id: string; partido_id: number; tipo: string; mensaje: string; ts: number }`
  - `AlertProvider({ children }): JSX.Element`
  - `useAlertas(): { alertas, toasts, noLeidas, marcarLeidas, seguidos, seguir, dejarDeSeguir, sigueA }`

- [ ] **Step 1: Create `frontend/lib/alertas.tsx`**

```tsx
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
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors related to `lib/alertas.tsx`.

- [ ] **Step 3: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/lib/alertas.tsx
git commit -m "feat: AlertProvider context with seguidos localStorage and WS filtering"
```

---

## Task 2: Alert UI Components

**Files:**
- Create: `frontend/app/components/BellMenu.tsx`
- Create: `frontend/app/components/SeguirEquipo.tsx`
- Create: `frontend/app/components/Providers.tsx`

**Interfaces:**
- Consumes: `useAlertas`, `AlertaItem` from `@/lib/alertas`
- Produces:
  - `BellMenu(): JSX.Element` — bell icon with badge + dropdown
  - `SeguirEquipo({ equipoId: number }): JSX.Element` — toggle button
  - `Providers({ children }): JSX.Element` — client wrapper

- [ ] **Step 1: Create `frontend/app/components/BellMenu.tsx`**

```tsx
"use client";
import { useEffect, useRef, useState } from "react";
import { useAlertas } from "@/lib/alertas";

export function BellMenu() {
  const { alertas, toasts, noLeidas, marcarLeidas } = useAlertas();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (
      typeof Notification !== "undefined" &&
      Notification.permission === "default"
    ) {
      Notification.requestPermission();
    }
  }, []);

  useEffect(() => {
    function onOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onOutside);
    return () => document.removeEventListener("mousedown", onOutside);
  }, []);

  const toggle = () => {
    setOpen((v) => !v);
    marcarLeidas();
  };

  return (
    <>
      <div ref={ref} className="relative">
        <button
          onClick={toggle}
          aria-label="Alertas"
          className="relative flex items-center justify-center rounded-lg p-2 text-muted transition hover:bg-panel2 hover:text-white"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="18"
            height="18"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          {noLeidas > 0 && (
            <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-accent text-xs font-bold text-ink">
              {noLeidas > 9 ? "9+" : noLeidas}
            </span>
          )}
        </button>

        {open && (
          <div className="absolute right-0 top-full z-50 mt-2 w-80 rounded-2xl border border-line bg-panel p-4 shadow-xl">
            <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted">
              Alertas recientes
            </p>
            {alertas.length === 0 ? (
              <p className="text-sm text-muted">Sin alertas en esta sesión.</p>
            ) : (
              <ul className="max-h-64 space-y-2 overflow-y-auto">
                {alertas.slice(0, 20).map((a) => (
                  <li
                    key={a.id}
                    className="border-t border-line pt-2 first:border-t-0 first:pt-0"
                  >
                    <span className="text-xs capitalize text-accent">
                      {a.tipo.replace(/_/g, " ")}
                    </span>
                    <p className="text-sm">{a.mensaje}</p>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      <div className="pointer-events-none fixed bottom-5 right-5 z-50 flex flex-col items-end gap-2">
        {toasts.map((t) => (
          <div
            key={t.id}
            className="pointer-events-auto w-72 rounded-2xl border border-accent/30 bg-panel p-4 shadow-xl"
          >
            <p className="text-xs capitalize text-accent">
              {t.tipo.replace(/_/g, " ")}
            </p>
            <p className="mt-0.5 text-sm">{t.mensaje}</p>
          </div>
        ))}
      </div>
    </>
  );
}
```

- [ ] **Step 2: Create `frontend/app/components/SeguirEquipo.tsx`**

```tsx
"use client";
import { useAlertas } from "@/lib/alertas";

export function SeguirEquipo({ equipoId }: { equipoId: number }) {
  const { sigueA, seguir, dejarDeSeguir } = useAlertas();
  const sigue = sigueA(equipoId);

  return (
    <button
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        sigue ? dejarDeSeguir(equipoId) : seguir(equipoId);
      }}
      className={`chip transition ${
        sigue
          ? "border-accent/60 text-accent"
          : "hover:border-accent/40 hover:text-white"
      }`}
    >
      {sigue ? "★ Siguiendo" : "☆ Seguir"}
    </button>
  );
}
```

- [ ] **Step 3: Create `frontend/app/components/Providers.tsx`**

```tsx
"use client";
import { AlertProvider } from "@/lib/alertas";

export function Providers({ children }: { children: React.ReactNode }) {
  return <AlertProvider>{children}</AlertProvider>;
}
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no new errors.

- [ ] **Step 5: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/app/components/BellMenu.tsx frontend/app/components/SeguirEquipo.tsx frontend/app/components/Providers.tsx
git commit -m "feat: BellMenu, SeguirEquipo, and Providers components"
```

---

## Task 3: Wire Alerts into Layout + Nav + Board

**Files:**
- Modify: `frontend/app/layout.tsx`
- Modify: `frontend/app/components/Nav.tsx`
- Modify: `frontend/app/page.tsx`

**Interfaces:**
- Consumes: `Providers`, `BellMenu`, `SeguirEquipo` from Tasks 1–2

- [ ] **Step 1: Modify `frontend/app/layout.tsx`**

Replace the entire file content with:

```tsx
import type { Metadata } from "next";
import "./globals.css";
import { Nav } from "./components/Nav";
import { Providers } from "./components/Providers";
import { BellMenu } from "./components/BellMenu";

export const metadata: Metadata = {
  title: "Solomillo · Predicciones",
  description: "Dashboard de predicciones de fútbol con Elo + ranking FIFA y Machine Learning",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        <Providers>
          <div className="mx-auto max-w-6xl px-5 py-6">
            <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h1 className="text-xl font-bold tracking-tight">
                  Solomillo<span className="text-accent"> · predicciones</span>
                </h1>
                <p className="text-sm text-muted">Elo + ranking FIFA, modelado con Weka</p>
              </div>
              <div className="flex items-center gap-2">
                <Nav />
                <BellMenu />
              </div>
            </header>
            {children}
            <footer className="mt-12 border-t border-line pt-5 text-xs text-muted">
              Modelos entrenados con datos históricos · probabilidades calibradas
            </footer>
          </div>
        </Providers>
      </body>
    </html>
  );
}
```

- [ ] **Step 2: Modify `frontend/app/components/Nav.tsx`**

Replace entire file:

```tsx
"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  { href: "/", label: "Predicciones" },
  { href: "/compare", label: "Comparar" },
  { href: "/models", label: "Modelos" },
  { href: "/elo", label: "Ranking Elo" },
];

export function Nav() {
  const path = usePathname();
  return (
    <nav className="flex gap-1 rounded-xl border border-line bg-panel/60 p-1">
      {LINKS.map((l) => {
        const active = l.href === "/" ? path === "/" : path.startsWith(l.href);
        return (
          <Link
            key={l.href}
            href={l.href}
            className={`navlink ${active ? "navlink-active" : ""}`}
          >
            {l.label}
          </Link>
        );
      })}
    </nav>
  );
}
```

- [ ] **Step 3: Modify `frontend/app/page.tsx`** — add SeguirEquipo buttons

In the `preds.map(...)` block, inside each `<Link>` card, add follow buttons for local and visitante teams after the `<ProbBar />`. Replace the `return (` block of the `preds.map` with:

```tsx
return (
  <Link
    key={pred.partido_id}
    href={`/match/${pred.partido_id}`}
    className="card flex flex-col transition hover:border-accent/60 hover:bg-panel2/60"
  >
    <div className="mb-3 flex items-center justify-between text-xs text-muted">
      <span>{fmtFecha(p?.fecha_hora ?? null)}</span>
      <span className="chip">
        {fav.label} · {Math.round(fav.conf * 100)}%
      </span>
    </div>

    <div className="mb-4 flex items-center justify-between gap-2">
      <Team nombre={local?.nombre ?? `#${p?.local_id}`} escudo={local?.escudo} />
      <span className="text-xs font-medium text-muted">vs</span>
      <Team nombre={visit?.nombre ?? `#${p?.visitante_id}`} escudo={visit?.escudo} right />
    </div>

    <ProbBar p={pred.probabilidades} />

    <div className="mt-3 flex gap-2">
      {local && <SeguirEquipo equipoId={local.id} />}
      {visit && <SeguirEquipo equipoId={visit.id} />}
    </div>
  </Link>
);
```

Also add the import at the top of `page.tsx`:

```tsx
import { SeguirEquipo } from "./components/SeguirEquipo";
```

- [ ] **Step 4: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/app/layout.tsx frontend/app/components/Nav.tsx frontend/app/page.tsx
git commit -m "feat: wire alerts into layout, add Comparar nav link, SeguirEquipo on board"
```

---

## Task 4: Comparador Page

**Files:**
- Create: `frontend/app/compare/page.tsx`
- Create: `frontend/app/components/CompareSelector.tsx`

**Interfaces:**
- Consumes:
  - `GET /equipos` → `Equipo[]`
  - `GET /equipos/{id}/estadisticas` → `{ metrica: string; valor: number; torneo_id: number }[]`
  - `GET /equipos/{id}/jugadores` → `{ id: number; nombre: string; posicion: string; numero: number }[]`
  - `GET /jugadores/{id}/estadisticas` → `{ metrica: string; valor: number; torneo_id: number }[]`
- Produces:
  - `ComparePage()` at route `/compare`
  - `CompareSelector<T>({ items, value, onChange, label, getKey, getLabel })`

- [ ] **Step 1: Create `frontend/app/components/CompareSelector.tsx`**

```tsx
"use client";
import { useState } from "react";

type Props<T> = {
  items: T[];
  value: T | null;
  onChange: (item: T) => void;
  label: string;
  getKey: (item: T) => string | number;
  getLabel: (item: T) => string;
};

export function CompareSelector<T>({
  items,
  value,
  onChange,
  label,
  getKey,
  getLabel,
}: Props<T>) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  const filtered = query
    ? items.filter((i) =>
        getLabel(i).toLowerCase().includes(query.toLowerCase())
      )
    : items;

  return (
    <div className="relative">
      <p className="mb-1 text-xs text-muted">{label}</p>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between rounded-xl border border-line bg-panel2 px-3 py-2 text-sm hover:border-accent/50 transition"
      >
        <span>{value ? getLabel(value) : "Elegir…"}</span>
        <span className="text-muted">▾</span>
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-1 w-full rounded-xl border border-line bg-panel shadow-xl">
          <div className="p-2">
            <input
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar…"
              className="w-full rounded-lg bg-panel2 px-3 py-1.5 text-sm outline-none placeholder:text-muted"
            />
          </div>
          <ul className="max-h-52 overflow-y-auto">
            {filtered.map((item) => (
              <li key={getKey(item)}>
                <button
                  onClick={() => {
                    onChange(item);
                    setOpen(false);
                    setQuery("");
                  }}
                  className="w-full px-3 py-2 text-left text-sm hover:bg-panel2 transition"
                >
                  {getLabel(item)}
                </button>
              </li>
            ))}
            {filtered.length === 0 && (
              <li className="px-3 py-2 text-sm text-muted">Sin resultados.</li>
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Create `frontend/app/compare/page.tsx`**

```tsx
"use client";
import { useEffect, useState } from "react";
import { get, Equipo } from "@/lib/api";
import { CompareSelector } from "../components/CompareSelector";
import { StatsRadar } from "../components/StatsRadar";
import { Crest } from "../components/Crest";

type Stat = { metrica: string; valor: number; torneo_id: number };
type Jugador = { id: number; nombre: string; posicion: string; numero: number };

type StatRow = {
  metrica: string;
  a: number | null;
  b: number | null;
};

function buildRows(statsA: Stat[], statsB: Stat[]): StatRow[] {
  const metricas = Array.from(
    new Set([...statsA.map((s) => s.metrica), ...statsB.map((s) => s.metrica)])
  ).sort();
  const mapA = new Map(statsA.map((s) => [s.metrica, s.valor]));
  const mapB = new Map(statsB.map((s) => [s.metrica, s.valor]));
  return metricas.map((m) => ({
    metrica: m,
    a: mapA.get(m) ?? null,
    b: mapB.get(m) ?? null,
  }));
}

export default function ComparePage() {
  const [modo, setModo] = useState<"equipos" | "jugadores">("equipos");
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [jugadoresA, setJugadoresA] = useState<Jugador[]>([]);
  const [jugadoresB, setJugadoresB] = useState<Jugador[]>([]);

  const [eqA, setEqA] = useState<Equipo | null>(null);
  const [eqB, setEqB] = useState<Equipo | null>(null);
  const [jugA, setJugA] = useState<Jugador | null>(null);
  const [jugB, setJugB] = useState<Jugador | null>(null);

  const [statsA, setStatsA] = useState<Stat[]>([]);
  const [statsB, setStatsB] = useState<Stat[]>([]);
  const [cargando, setCargando] = useState(false);

  useEffect(() => {
    get<Equipo[]>("/equipos").then(setEquipos).catch(() => {});
  }, []);

  useEffect(() => {
    if (eqA)
      get<Jugador[]>(`/equipos/${eqA.id}/jugadores`)
        .then(setJugadoresA)
        .catch(() => setJugadoresA([]));
    else setJugadoresA([]);
  }, [eqA]);

  useEffect(() => {
    if (eqB)
      get<Jugador[]>(`/equipos/${eqB.id}/jugadores`)
        .then(setJugadoresB)
        .catch(() => setJugadoresB([]));
    else setJugadoresB([]);
  }, [eqB]);

  useEffect(() => {
    if (modo === "equipos") {
      if (!eqA || !eqB) return;
      setCargando(true);
      Promise.all([
        get<Stat[]>(`/equipos/${eqA.id}/estadisticas`),
        get<Stat[]>(`/equipos/${eqB.id}/estadisticas`),
      ])
        .then(([a, b]) => { setStatsA(a); setStatsB(b); })
        .catch(() => {})
        .finally(() => setCargando(false));
    } else {
      if (!jugA || !jugB) return;
      setCargando(true);
      Promise.all([
        get<Stat[]>(`/jugadores/${jugA.id}/estadisticas`),
        get<Stat[]>(`/jugadores/${jugB.id}/estadisticas`),
      ])
        .then(([a, b]) => { setStatsA(a); setStatsB(b); })
        .catch(() => {})
        .finally(() => setCargando(false));
    }
  }, [modo, eqA, eqB, jugA, jugB]);

  const rows = buildRows(statsA, statsB);
  const nombreA = modo === "equipos" ? eqA?.nombre : jugA?.nombre;
  const nombreB = modo === "equipos" ? eqB?.nombre : jugB?.nombre;
  const showResults = (modo === "equipos" ? eqA && eqB : jugA && jugB) && rows.length > 0;

  return (
    <div className="space-y-5">
      <div className="flex gap-2">
        {(["equipos", "jugadores"] as const).map((m) => (
          <button
            key={m}
            onClick={() => { setModo(m); setStatsA([]); setStatsB([]); }}
            className={`navlink capitalize ${modo === m ? "navlink-active" : ""}`}
          >
            {m}
          </button>
        ))}
      </div>

      {modo === "equipos" ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <CompareSelector
            items={equipos}
            value={eqA}
            onChange={setEqA}
            label="Equipo A"
            getKey={(e) => e.id}
            getLabel={(e) => e.nombre}
          />
          <CompareSelector
            items={equipos}
            value={eqB}
            onChange={setEqB}
            label="Equipo B"
            getKey={(e) => e.id}
            getLabel={(e) => e.nombre}
          />
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <CompareSelector
              items={equipos}
              value={eqA}
              onChange={(e) => { setEqA(e); setJugA(null); }}
              label="Equipo del jugador A"
              getKey={(e) => e.id}
              getLabel={(e) => e.nombre}
            />
            {jugadoresA.length > 0 && (
              <CompareSelector
                items={jugadoresA}
                value={jugA}
                onChange={setJugA}
                label="Jugador A"
                getKey={(j) => j.id}
                getLabel={(j) => `${j.nombre} (${j.posicion})`}
              />
            )}
          </div>
          <div className="space-y-2">
            <CompareSelector
              items={equipos}
              value={eqB}
              onChange={(e) => { setEqB(e); setJugB(null); }}
              label="Equipo del jugador B"
              getKey={(e) => e.id}
              getLabel={(e) => e.nombre}
            />
            {jugadoresB.length > 0 && (
              <CompareSelector
                items={jugadoresB}
                value={jugB}
                onChange={setJugB}
                label="Jugador B"
                getKey={(j) => j.id}
                getLabel={(j) => `${j.nombre} (${j.posicion})`}
              />
            )}
          </div>
        </div>
      )}

      {!showResults && !cargando && (
        <p className="text-sm text-muted">
          {modo === "equipos"
            ? "Elegí dos equipos para comparar."
            : "Elegí el equipo de cada jugador y luego el jugador."}
        </p>
      )}

      {cargando && <p className="text-sm text-muted">Cargando estadísticas…</p>}

      {showResults && !cargando && (
        <div className="space-y-4">
          {rows.length > 2 && (
            <div className="card">
              <h2 className="mb-3 text-sm font-semibold text-muted">Radar</h2>
              <StatsRadar rows={rows} nombreA={nombreA!} nombreB={nombreB!} />
            </div>
          )}

          <div className="card overflow-x-auto">
            <div className="mb-3 grid grid-cols-3 text-xs font-semibold uppercase tracking-wide text-muted">
              <span>
                {modo === "equipos" && eqA && (
                  <span className="flex items-center gap-2">
                    <Crest src={eqA.escudo} size={16} />
                    {eqA.nombre}
                  </span>
                )}
                {modo === "jugadores" && jugA?.nombre}
              </span>
              <span className="text-center">Métrica</span>
              <span className="text-right">
                {modo === "equipos" && eqB && (
                  <span className="flex items-center justify-end gap-2">
                    {eqB.nombre}
                    <Crest src={eqB.escudo} size={16} />
                  </span>
                )}
                {modo === "jugadores" && jugB?.nombre}
              </span>
            </div>
            <table className="w-full text-sm">
              <tbody>
                {rows.map((row) => {
                  const aNum = row.a ?? 0;
                  const bNum = row.b ?? 0;
                  return (
                    <tr key={row.metrica} className="border-t border-line">
                      <td className="py-2 tabular-nums">
                        <span
                          className={
                            aNum > bNum ? "font-bold text-accent" : "text-muted"
                          }
                        >
                          {row.a ?? "—"}
                        </span>
                      </td>
                      <td className="py-2 text-center text-xs text-muted">
                        {row.metrica}
                      </td>
                      <td className="py-2 text-right tabular-nums">
                        <span
                          className={
                            bNum > aNum ? "font-bold text-accent" : "text-muted"
                          }
                        >
                          {row.b ?? "—"}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/app/compare/page.tsx frontend/app/components/CompareSelector.tsx
git commit -m "feat: comparador page with equipos and jugadores modes"
```

---

## Task 5: Stats Radar Chart

**Files:**
- Create: `frontend/app/components/StatsRadar.tsx`

**Interfaces:**
- Consumes: `StatRow = { metrica: string; a: number | null; b: number | null }`
- Produces: `StatsRadar({ rows, nombreA, nombreB }): JSX.Element`

- [ ] **Step 1: Create `frontend/app/components/StatsRadar.tsx`**

```tsx
"use client";
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  ResponsiveContainer,
  Legend,
} from "recharts";

type StatRow = { metrica: string; a: number | null; b: number | null };

type Props = {
  rows: StatRow[];
  nombreA: string;
  nombreB: string;
};

function normalize(rows: StatRow[]): { metrica: string; a: number; b: number }[] {
  return rows.map((r) => {
    const max = Math.max(r.a ?? 0, r.b ?? 0, 1);
    return {
      metrica: r.metrica.replace(/_/g, " "),
      a: Math.round(((r.a ?? 0) / max) * 100),
      b: Math.round(((r.b ?? 0) / max) * 100),
    };
  });
}

export function StatsRadar({ rows, nombreA, nombreB }: Props) {
  const data = normalize(rows.slice(0, 8));

  if (data.length < 3) {
    return <p className="text-sm text-muted">No hay suficientes métricas para el radar.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <RadarChart data={data}>
        <PolarGrid stroke="#252e44" />
        <PolarAngleAxis dataKey="metrica" tick={{ fill: "#8a97b4", fontSize: 11 }} />
        <Radar
          name={nombreA}
          dataKey="a"
          stroke="#22d3ee"
          fill="#22d3ee"
          fillOpacity={0.2}
        />
        <Radar
          name={nombreB}
          dataKey="b"
          stroke="#f472b6"
          fill="#f472b6"
          fillOpacity={0.2}
        />
        <Legend
          wrapperStyle={{ fontSize: 12, color: "#8a97b4" }}
        />
      </RadarChart>
    </ResponsiveContainer>
  );
}
```

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/app/components/StatsRadar.tsx
git commit -m "feat: StatsRadar recharts component for comparador"
```

---

## Task 6: PitchFormation Component + Match Detail Integration

**Files:**
- Create: `frontend/app/components/PitchFormation.tsx`
- Modify: `frontend/app/match/[id]/page.tsx`

**Interfaces:**
- Consumes: `GET /equipos/{id}/jugadores` → `{ id: number; nombre: string; posicion: string; numero: number }[]`
- Produces: `PitchFormation({ local, visitante, nombreLocal, nombreVisitante }): JSX.Element`
  - `local` and `visitante` are arrays of `{ id: number; nombre: string; posicion: string; numero: number }`

- [ ] **Step 1: Create `frontend/app/components/PitchFormation.tsx`**

```tsx
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

function linea(posicion: string): Linea {
  const p = posicion.toLowerCase();
  for (const [l, keys] of Object.entries(KEYS) as [Linea, string[]][]) {
    if (keys.some((k) => p.includes(k))) return l;
  }
  return "MID";
}

function agrupar(jugadores: Jugador[]): Record<Linea, Jugador[]> {
  const grupos: Record<Linea, Jugador[]> = { GK: [], DEF: [], MID: [], FWD: [] };
  jugadores.forEach((j) => grupos[linea(j.posicion)].push(j));
  return grupos;
}

function PlayerChip({
  jugador,
  color,
}: {
  jugador: Jugador;
  color: string;
}) {
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
          <div
            key={l}
            className="flex items-center justify-center gap-3 py-2"
          >
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
      className="relative rounded-xl border border-line overflow-hidden"
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
        className="absolute left-1/2 top-0 bottom-0 w-px -translate-x-1/2"
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
```

- [ ] **Step 2: Modify `frontend/app/match/[id]/page.tsx`**

Add formation state and fetch. After the existing `const [eloV, setEloV]` line, add:

```tsx
const [plantelLocal, setPlantelLocal] = useState<{ id: number; nombre: string; posicion: string; numero: number }[]>([]);
const [plantelVisit, setPlantelVisit] = useState<{ id: number; nombre: string; posicion: string; numero: number }[]>([]);
```

Inside the `useEffect` block where `l` and `v` are resolved, after the Elo fetch lines add:

```tsx
if (l) get<{ id: number; nombre: string; posicion: string; numero: number }[]>(`/equipos/${l.id}/jugadores`).then(setPlantelLocal).catch(() => {});
if (v) get<{ id: number; nombre: string; posicion: string; numero: number }[]>(`/equipos/${v.id}/jugadores`).then(setPlantelVisit).catch(() => {});
```

Add the import at the top:

```tsx
import { PitchFormation } from "../../components/PitchFormation";
```

After the Elo card, add the formation card:

```tsx
<div className="card">
  <h2 className="mb-3 text-sm font-semibold text-muted">Formación probable</h2>
  <PitchFormation
    local={plantelLocal}
    visitante={plantelVisit}
    nombreLocal={local?.nombre ?? `#${partido.local_id}`}
    nombreVisitante={visit?.nombre ?? `#${partido.visitante_id}`}
  />
</div>
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo/frontend && npx tsc --noEmit 2>&1 | head -30
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
cd /home/cauchothegaucho/Repositorios/solomillo
git add frontend/app/components/PitchFormation.tsx frontend/app/match/[id]/page.tsx
git commit -m "feat: PitchFormation visual component and formation card on match detail"
```

---

## Self-Review

**Spec coverage:**
- ✓ Alertas in-page + browser Notification → Tasks 1–3
- ✓ Seguir equipo → Tasks 2–3 (SeguirEquipo button)
- ✓ Comparar equipos → Task 4
- ✓ Comparar jugadores → Task 4
- ✓ Radar chart → Task 5
- ✓ Win probability on match → already existed, formation card added in Task 6
- ✓ Formación visual → Task 6

**Placeholder scan:** None found.

**Type consistency:**
- `AlertaItem` defined in Task 1, used in Tasks 2–3 ✓
- `Stat` type used consistently across Task 4 ✓
- `StatRow` produced in Task 4 (`compare/page.tsx`), consumed in Task 5 (`StatsRadar`) — note Task 5's `StatsRadar` defines its own local `StatRow` type, which matches the shape from Task 4 ✓
- `Jugador` type in `PitchFormation` matches the `/equipos/{id}/jugadores` response shape used in Task 6 ✓
