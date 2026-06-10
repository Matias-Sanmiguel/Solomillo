"use client";

import { useEffect, useState } from "react";
import { API, WS, get } from "@/lib/api";

type Torneo = { id: number; nombre: string; temporada: string };
type Equipo = { id: number; nombre: string; entrenador: string; escudo: string; estadio: string };
type Partido = {
  id: number;
  torneo_id: number;
  local_id: number;
  visitante_id: number;
  fecha_hora: string;
  estadio: string;
};
type Posicion = { equipo_id: number; puntos: number; gf: number; gc: number; dif: number };
type FeedItem = { id: number; texto: string; tag: string };

export default function Home() {
  const [torneos, setTorneos] = useState<Torneo[]>([]);
  const [torneoId, setTorneoId] = useState<number | null>(null);
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [partidos, setPartidos] = useState<Partido[]>([]);
  const [posiciones, setPosiciones] = useState<Posicion[]>([]);
  const [feed, setFeed] = useState<FeedItem[]>([]);
  const [pred, setPred] = useState<string>("");

  const cargar = (tid: number | null) => {
    get<Equipo[]>("/equipos").then(setEquipos).catch(() => {});
    get<Partido[]>("/partidos").then(setPartidos).catch(() => {});
    if (tid != null) {
      get<Posicion[]>(`/torneos/${tid}/posiciones`).then(setPosiciones).catch(() => {});
    }
  };

  useEffect(() => {
    get<Torneo[]>("/torneos")
      .then((ts) => {
        setTorneos(ts);
        if (ts.length > 0) setTorneoId(ts[0].id);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    cargar(torneoId);
  }, [torneoId]);

  useEffect(() => {
    const ws = new WebSocket(WS);
    ws.onmessage = (e) => {
      const d = JSON.parse(e.data);
      const texto = d.mensaje ?? `${d.tipo} · min ${d.minuto} · partido ${d.partido_id}`;
      const tag = d.mensaje ? "alerta" : "evento";
      setFeed((f) => [{ id: Date.now() + Math.random(), texto, tag }, ...f].slice(0, 30));
      cargar(torneoId);
    };
    return () => ws.close();
  }, [torneoId]);

  const equipo = (id: number) => equipos.find((e) => e.id === id);
  const nombre = (id: number) => equipo(id)?.nombre ?? `#${id}`;

  const torneoActual = torneos.find((t) => t.id === torneoId);
  const partidosTorneo = partidos
    .filter((p) => p.torneo_id === torneoId)
    .sort((a, b) => a.fecha_hora.localeCompare(b.fecha_hora));

  const fmtFecha = (iso: string) =>
    new Date(iso).toLocaleString("es-AR", {
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });

  const predecir = async () => {
    const partidoId = partidosTorneo[0]?.id ?? 1;
    try {
      const r = await get<{ probabilidades: Record<string, number> }>(`/ml/predicciones/${partidoId}`);
      setPred(
        Object.entries(r.probabilidades)
          .map(([k, v]) => `${k} ${(v * 100).toFixed(0)}%`)
          .join("  ·  ")
      );
    } catch {
      setPred("Sin modelo. Entrenar en " + API + "/docs");
    }
  };

  const Escudo = ({ id }: { id: number }) => {
    const url = equipo(id)?.escudo;
    return url ? <img className="escudo" src={url} alt="" /> : null;
  };

  return (
    <main>
      <h1>Solomillo</h1>
      <p className="sub">Estadísticas deportivas en tiempo real · datos de API-Football</p>

      {torneos.length > 0 && (
        <div className="selector">
          {torneos.map((t) => (
            <button
              key={t.id}
              className={t.id === torneoId ? "activo" : ""}
              onClick={() => setTorneoId(t.id)}
            >
              {t.nombre} {t.temporada}
            </button>
          ))}
        </div>
      )}

      <div className="grid">
        <div className="card">
          <h2>Tabla de posiciones{torneoActual ? ` · ${torneoActual.temporada}` : ""}</h2>
          <table>
            <thead>
              <tr>
                <th>Equipo</th>
                <th>Pts</th>
                <th>GF</th>
                <th>GC</th>
                <th>Dif</th>
              </tr>
            </thead>
            <tbody>
              {posiciones.map((p) => (
                <tr key={p.equipo_id}>
                  <td>
                    <Escudo id={p.equipo_id} />
                    {nombre(p.equipo_id)}
                  </td>
                  <td>{p.puntos}</td>
                  <td>{p.gf}</td>
                  <td>{p.gc}</td>
                  <td>{p.dif}</td>
                </tr>
              ))}
              {posiciones.length === 0 && (
                <tr>
                  <td colSpan={5}>Sin datos aún. Ingestá eventos.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h2>Feed en vivo</h2>
          <ul className="feed">
            {feed.map((f) => (
              <li key={f.id}>
                <span className="tag">{f.tag}</span>
                {f.texto}
              </li>
            ))}
            {feed.length === 0 && <li>Esperando eventos…</li>}
          </ul>
        </div>

        <div className="card">
          <h2>Fixture{torneoActual ? ` · ${torneoActual.temporada}` : ""}</h2>
          <ul className="fixture">
            {partidosTorneo.slice(0, 20).map((p) => (
              <li key={p.id}>
                <span className="vs">
                  <Escudo id={p.local_id} />
                  {nombre(p.local_id)} vs {nombre(p.visitante_id)}
                  <Escudo id={p.visitante_id} />
                </span>
                <span className="meta">
                  {fmtFecha(p.fecha_hora)}
                  {p.estadio ? ` · ${p.estadio}` : ""}
                </span>
              </li>
            ))}
            {partidosTorneo.length === 0 && <li>Sin partidos para este torneo.</li>}
          </ul>
        </div>

        <div className="card">
          <h2>Equipos</h2>
          <table>
            <tbody>
              {equipos.map((e) => (
                <tr key={e.id}>
                  <td>
                    {e.escudo && <img className="escudo" src={e.escudo} alt="" />}
                    {e.nombre}
                  </td>
                  <td>{e.entrenador || e.estadio}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h2>Predicción ML</h2>
          <button onClick={predecir}>Predecir resultado</button>
          <p style={{ marginTop: "1rem" }}>{pred}</p>
        </div>
      </div>
    </main>
  );
}
