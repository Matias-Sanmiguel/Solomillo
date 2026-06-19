# Design: Alertas, Comparador, Formación Visual

Date: 2026-06-17

## Feature 1 — Alertas in-page

### Goal
Notificar al usuario de eventos de partidos (goles, tarjetas, fin) para los equipos que sigue.

### Architecture
- Pure frontend: usa WebSocket existente (`WS` env var → `/ws/eventos`).
- Usuario sigue equipos via `localStorage` key `solomillo:seguidos` → array de `equipo_id`.
- `AlertManager` (componente global en `layout.tsx`): conecta WS, filtra eventos por equipos seguidos, emite toasts.
- `useAlertas` hook: expone lista de alertas no-leídas + función `marcarLeídas`.
- `Nav` muestra campana con badge. Click abre dropdown con historial de sesión.
- Opt-in browser `Notification` API: si usuario acepta permiso, también dispara notificación nativa.

### Data flow
```
WS event → AlertManager → filter by seguidos → toast (+ optional browser notif) → bell badge++
```

### Components
- `AlertManager` — provider global, no UI propio
- `useAlertas` — hook de contexto
- `AlertToast` — toast bottom-right, auto-dismiss 5s
- `BellMenu` — dropdown en Nav
- `SeguirEquipo` — botón toggle en cards de equipo (main board)

### Non-goals
- No backend changes
- No push notifications (service worker)
- No persistencia cross-session de alertas

---

## Feature 2 — Comparador

### Goal
Comparar stats de 2 equipos o 2 jugadores side-by-side.

### Architecture
- Nueva ruta `/compare` (Next.js page).
- Tabs: Equipos / Jugadores.
- Dos selectores con búsqueda (combobox simple).
- Fetch en paralelo: `/equipos/{id}/estadisticas` o `/jugadores/{id}/estadisticas`.
- Stats como tabla side-by-side + radar chart (Recharts).
- Sin cambios de backend.

### Components
- `app/compare/page.tsx` — página principal
- `CompareSelector` — dropdown de búsqueda reutilizable
- `StatsTable` — tabla diff side-by-side
- `RadarChart` — Recharts RadarChart

### Data
`/equipos/{id}/estadisticas` devuelve `[{metrica, valor, torneo_id}]`. Pivot por métrica para comparar.

---

## Feature 3 — Formación visual en detalle de partido

### Goal
Mostrar formación probable de ambos equipos en `/match/[id]`, agrupada por posición.

### Architecture
- Fetch `/equipos/{id}/jugadores` para local y visitante (ya existe).
- Agrupar por campo `posicion`: mapeamos strings → línea (GK, DEF, MID, FWD).
- Render: cancha CSS verde con 4 filas (una por línea), chips de jugador.
- Ambos equipos en misma cancha: local arriba, visitante abajo (orientados).
- Sin cambios de backend.

### Components
- `PitchFormation` — componente cancha + jugadores
- Lógica de agrupado por posición: dict de strings → categoría

### Position mapping
```
GK: portero, goalkeeper, arquero, gk
DEF: defensa, defender, central, lateral, df, cb, lb, rb
MID: mediocampista, midfielder, mf, cm, dm, am
FWD: delantero, forward, fw, st, cf, lw, rw, winger
```

---

## Branch

`feature/alertas-comparador-formacion`
