"use client";

import { useEffect, useState } from "react";
import { API, WS, get } from "@/lib/api";

type Equipo = { id: number; nombre: string; entrenador: string };
type Posicion = { equipo_id: number; puntos: number; gf: number; gc: number; dif: number };
type FeedItem = { id: number; texto: string; tag: string };

export default function Home() {
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [posiciones, setPosiciones] = useState<Posicion[]>([]);
  const [feed, setFeed] = useState<FeedItem[]>([]);
  const [pred, setPred] = useState<string>("");

  const cargar = () => {
    get<Equipo[]>("/equipos").then(setEquipos).catch(() => {});
    get<Posicion[]>("/torneos/1/posiciones").then(setPosiciones).catch(() => {});
  };

  useEffect(() => {
    cargar();
    const ws = new WebSocket(WS);
    ws.onmessage = (e) => {
      const d = JSON.parse(e.data);
      const texto = d.mensaje ?? `${d.tipo} · min ${d.minuto} · partido ${d.partido_id}`;
      const tag = d.mensaje ? "alerta" : "evento";
      setFeed((f) => [{ id: Date.now() + Math.random(), texto, tag }, ...f].slice(0, 30));
      cargar();
    };
    return () => ws.close();
  }, []);

  const nombre = (id: number) => equipos.find((e) => e.id === id)?.nombre ?? `#${id}`;

  const predecir = async () => {
    try {
      const r = await get<{ probabilidades: Record<string, number> }>("/ml/predicciones/1");
      const p = r.probabilidades;
      setPred(
        Object.entries(p)
          .map(([k, v]) => `${k} ${(v * 100).toFixed(0)}%`)
          .join("  ·  ")
      );
    } catch {
      setPred("Sin modelo. Entrenar en " + API + "/docs");
    }
  };

  return (
    <main>
      <h1>Solomillo</h1>
      <p className="sub">Estadísticas deportivas en tiempo real · Copa Mundial 2022</p>

      <div className="grid">
        <div className="card">
          <h2>Tabla de posiciones</h2>
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
                  <td>{nombre(p.equipo_id)}</td>
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
          <h2>Equipos</h2>
          <table>
            <tbody>
              {equipos.map((e) => (
                <tr key={e.id}>
                  <td>{e.nombre}</td>
                  <td>{e.entrenador}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="card">
          <h2>Predicción ML · final</h2>
          <button onClick={predecir}>Predecir resultado</button>
          <p style={{ marginTop: "1rem" }}>{pred}</p>
        </div>
      </div>
    </main>
  );
}
