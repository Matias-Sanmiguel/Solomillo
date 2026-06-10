"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  API,
  WS,
  get,
  fmtFecha,
  type Torneo,
  type Equipo,
  type Jugador,
  type Partido,
  type Posicion,
  type Proyeccion,
  type StatJugador,
  type FeedItem,
} from "@/lib/api";

type Conn = "conectando" | "online" | "offline";

export default function Home() {
  const [torneos, setTorneos] = useState<Torneo[]>([]);
  const [torneoId, setTorneoId] = useState<number | null>(null);
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [partidos, setPartidos] = useState<Partido[]>([]);
  const [posiciones, setPosiciones] = useState<Posicion[]>([]);
  const [proyeccion, setProyeccion] = useState<Proyeccion[]>([]);
  const [feed, setFeed] = useState<FeedItem[]>([]);
  const [pred, setPred] = useState<{ k: string; v: number }[] | null>(null);
  const [predMsg, setPredMsg] = useState<string>("");
  const [conn, setConn] = useState<Conn>("conectando");
  const [detalle, setDetalle] = useState<{ equipo: Equipo; jugadores: Jugador[] } | null>(null);
  const [tema, setTema] = useState<"light" | "dark">("dark");

  useEffect(() => {
    const actual = document.documentElement.dataset.theme;
    setTema(actual === "light" ? "light" : "dark");
  }, []);

  const toggleTema = () => {
    const siguiente = tema === "dark" ? "light" : "dark";
    setTema(siguiente);
    document.documentElement.dataset.theme = siguiente;
    try {
      localStorage.setItem("tema", siguiente);
    } catch {}
  };

  const cargar = useCallback((tid: number | null) => {
    get<Equipo[]>("/equipos").then(setEquipos).catch(() => {});
    get<Partido[]>("/partidos").then(setPartidos).catch(() => {});
    if (tid != null) {
      get<Posicion[]>(`/torneos/${tid}/posiciones`).then(setPosiciones).catch(() => {});
      get<Proyeccion[]>(`/ml/proyeccion/${tid}`).then(setProyeccion).catch(() => setProyeccion([]));
    }
  }, []);

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
  }, [torneoId, cargar]);

  // WebSocket con reconexión automática: si el backend reinicia, el feed se recupera solo.
  const torneoRef = useRef(torneoId);
  torneoRef.current = torneoId;
  useEffect(() => {
    let ws: WebSocket;
    let retry: ReturnType<typeof setTimeout>;
    let cerrado = false;

    const conectar = () => {
      setConn("conectando");
      ws = new WebSocket(WS);
      ws.onopen = () => setConn("online");
      ws.onmessage = (e) => {
        const d = JSON.parse(e.data);
        const texto = d.mensaje ?? `${d.tipo} · min ${d.minuto} · partido ${d.partido_id}`;
        const tag = d.mensaje ? "alerta" : "evento";
        setFeed((f) => [{ id: Date.now() + Math.random(), texto, tag }, ...f].slice(0, 30));
        cargar(torneoRef.current);
      };
      ws.onclose = () => {
        setConn("offline");
        if (!cerrado) retry = setTimeout(conectar, 3000);
      };
      ws.onerror = () => ws.close();
    };

    conectar();
    return () => {
      cerrado = true;
      clearTimeout(retry);
      ws?.close();
    };
  }, [cargar]);

  const equipo = (id: number) => equipos.find((e) => e.id === id);
  const nombre = (id: number) => equipo(id)?.nombre ?? `#${id}`;

  const torneoActual = torneos.find((t) => t.id === torneoId);
  const partidosTorneo = partidos
    .filter((p) => p.torneo_id === torneoId)
    .sort((a, b) => a.fecha_hora.localeCompare(b.fecha_hora));

  const proximoPartido = partidosTorneo.find((p) => new Date(p.fecha_hora) >= new Date()) ?? partidosTorneo[0];

  const predecir = async () => {
    const partidoId = proximoPartido?.id ?? 1;
    setPred(null);
    setPredMsg("Calculando…");
    try {
      const r = await get<{ probabilidades: Record<string, number> }>(`/ml/predicciones/${partidoId}`);
      setPred(Object.entries(r.probabilidades).map(([k, v]) => ({ k, v })));
      setPredMsg("");
    } catch {
      setPred(null);
      setPredMsg(`Sin modelo activo. Entrenar en ${API}/docs`);
    }
  };

  const abrirEquipo = async (e: Equipo) => {
    if (detalle?.equipo.id === e.id) {
      setDetalle(null);
      return;
    }
    try {
      const jugadores = await get<Jugador[]>(`/equipos/${e.id}/jugadores`);
      setDetalle({ equipo: e, jugadores });
    } catch {
      setDetalle({ equipo: e, jugadores: [] });
    }
  };

  const Escudo = ({ id }: { id: number }) => {
    const url = equipo(id)?.escudo;
    return url ? <img className="escudo" src={url} alt="" /> : null;
  };

  return (
    <main>
      <header className="head">
        <div>
          <h1>
            Solomillo<span className="cursor">_</span>
          </h1>
          <p className="sub">Estadísticas deportivas en tiempo real · datos de API-Football</p>
        </div>
        <div className="head-right">
          <button
            className="tema-btn"
            onClick={toggleTema}
            aria-pressed={tema === "dark"}
            aria-label={`Cambiar a modo ${tema === "dark" ? "claro" : "oscuro"}`}
          >
            <span className="ico" aria-hidden="true">
              {tema === "dark" ? "☀" : "☾"}
            </span>
            {tema === "dark" ? "Claro" : "Oscuro"}
          </button>
          <span className={`estado ${conn}`} role="status" aria-live="polite">
            <i aria-hidden="true" /> {conn === "online" ? "En vivo" : conn === "conectando" ? "Conectando" : "Reconectando"}
          </span>
        </div>
      </header>

      {torneos.length > 0 && (
        <div className="selector" role="tablist" aria-label="Torneos">
          {torneos.map((t) => (
            <button
              key={t.id}
              role="tab"
              aria-selected={t.id === torneoId}
              className={t.id === torneoId ? "activo" : ""}
              onClick={() => setTorneoId(t.id)}
            >
              {t.nombre} {t.temporada}
            </button>
          ))}
        </div>
      )}

      <div className="grid">
        <section className="card" aria-label="Tabla de posiciones">
          <h2>Tabla de posiciones{torneoActual ? ` · ${torneoActual.temporada}` : ""}</h2>
          <table>
            <thead>
              <tr>
                <th scope="col">Equipo</th>
                <th scope="col">Pts</th>
                <th scope="col">GF</th>
                <th scope="col">GC</th>
                <th scope="col">Dif</th>
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
                  <td className={p.dif > 0 ? "pos" : p.dif < 0 ? "neg" : ""}>
                    {p.dif > 0 ? `+${p.dif}` : p.dif}
                  </td>
                </tr>
              ))}
              {posiciones.length === 0 && (
                <tr>
                  <td colSpan={5} className="vacio">
                    Sin datos aún. Ingestá eventos.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </section>

        <section className="card" aria-label="Feed en vivo">
          <h2>Feed en vivo</h2>
          <ul className="feed">
            {feed.map((f) => (
              <li key={f.id}>
                <span className={`tag ${f.tag}`}>{f.tag}</span>
                {f.texto}
              </li>
            ))}
            {feed.length === 0 && <li className="vacio">Esperando eventos…</li>}
          </ul>
        </section>

        <section className="card" aria-label="Fixture">
          <h2>Fixture{torneoActual ? ` · ${torneoActual.temporada}` : ""}</h2>
          <ul className="fixture">
            {partidosTorneo.slice(0, 20).map((p) => (
              <li key={p.id} className={p.id === proximoPartido?.id ? "proximo" : ""}>
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
            {partidosTorneo.length === 0 && <li className="vacio">Sin partidos para este torneo.</li>}
          </ul>
        </section>

        <section className="card" aria-label="Equipos">
          <h2>Equipos</h2>
          <p className="hint">Tocá un equipo para ver su plantel.</p>
          <ul className="equipos">
            {equipos.map((e) => (
              <li key={e.id}>
                <button
                  className={`equipo-btn ${detalle?.equipo.id === e.id ? "abierto" : ""}`}
                  aria-expanded={detalle?.equipo.id === e.id}
                  onClick={() => abrirEquipo(e)}
                >
                  <span className="eq-nombre">
                    {e.escudo && <img className="escudo" src={e.escudo} alt="" />}
                    {e.nombre}
                  </span>
                  <span className="meta">{e.entrenador || e.estadio}</span>
                </button>
              </li>
            ))}
          </ul>
          {detalle && (
            <div className="plantel">
              <h3>{detalle.equipo.nombre}</h3>
              {detalle.jugadores.length === 0 && <p className="vacio">Sin jugadores cargados.</p>}
              {detalle.jugadores.map((j) => (
                <JugadorRow key={j.id} jugador={j} />
              ))}
            </div>
          )}
        </section>

        <section className="card" aria-label="Proyección ML">
          <h2>Proyección ML{torneoActual ? ` · ${torneoActual.temporada}` : ""}</h2>
          {proyeccion.length === 0 ? (
            <p className="vacio">Sin modelo activo todavía. Entrená el modelo de resultado.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">Equipo</th>
                  <th scope="col">Pts</th>
                  <th scope="col">xPts</th>
                </tr>
              </thead>
              <tbody>
                {proyeccion.map((p) => (
                  <tr key={p.equipo_id}>
                    <td>{p.posicion_proyectada}</td>
                    <td>
                      <Escudo id={p.equipo_id} />
                      {nombre(p.equipo_id)}
                    </td>
                    <td>{p.puntos_actuales}</td>
                    <td className="xpts">{p.puntos_esperados.toFixed(1)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <section className="card" aria-label="Predicción de resultado">
          <h2>Predicción de resultado</h2>
          {proximoPartido ? (
            <p className="hint">
              {nombre(proximoPartido.local_id)} vs {nombre(proximoPartido.visitante_id)} ·{" "}
              {fmtFecha(proximoPartido.fecha_hora)}
            </p>
          ) : (
            <p className="hint">Sin partidos para predecir.</p>
          )}
          <button className="accion" onClick={predecir}>
            Predecir
          </button>
          {pred && (
            <div className="barras">
              {pred.map(({ k, v }) => (
                <div className="barra" key={k}>
                  <span className="etq">{k}</span>
                  <div
                    className="track"
                    role="progressbar"
                    aria-label={k}
                    aria-valuenow={Math.round(v * 100)}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    <div className="fill" style={{ width: `${Math.round(v * 100)}%` }} />
                  </div>
                  <span className="pct">{(v * 100).toFixed(0)}%</span>
                </div>
              ))}
            </div>
          )}
          {predMsg && <p className="predmsg">{predMsg}</p>}
        </section>
      </div>
    </main>
  );
}

function JugadorRow({ jugador }: { jugador: Jugador }) {
  const [stats, setStats] = useState<StatJugador[] | null>(null);
  const toggle = () => {
    if (stats) {
      setStats(null);
      return;
    }
    get<StatJugador[]>(`/jugadores/${jugador.id}/estadisticas`)
      .then(setStats)
      .catch(() => setStats([]));
  };
  return (
    <div className="jugador">
      <button className="jbtn" aria-expanded={stats !== null} onClick={toggle}>
        <span className="n">{jugador.numero}</span>
        {jugador.nombre}
        <span className="meta">{jugador.posicion}</span>
      </button>
      {stats && (
        <div className="jstats">
          {stats.length === 0 && <span className="vacio">Sin estadísticas.</span>}
          {stats.map((s) => (
            <span className="chip" key={s.metrica}>
              {s.metrica}: {s.valor}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
