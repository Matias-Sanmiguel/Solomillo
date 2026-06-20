"use client";

import { useEffect, useState } from "react";
import {
  get,
  send,
  getToken,
  Escenario,
  SimFecha,
  SimMundial,
  Reentrenamiento,
} from "@/lib/api";

export default function AdminPage() {
  const [reentrenar, setReentrenar] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [fecha, setFecha] = useState<SimFecha | null>(null);
  const [mundial, setMundial] = useState<SimMundial | null>(null);
  const [reentrenoManual, setReentrenoManual] = useState<Reentrenamiento>(null);
  const [escenarios, setEscenarios] = useState<Escenario[]>([]);
  const [nombre, setNombre] = useState("");
  const [descripcion, setDescripcion] = useState("");

  const autenticado = !!getToken();

  function cargarEscenarios() {
    get<Escenario[]>("/sim/escenarios").then(setEscenarios).catch(() => {});
  }
  useEffect(cargarEscenarios, []);

  async function run<T>(key: string, fn: () => Promise<T>, ok?: (r: T) => void) {
    setBusy(key);
    setMsg(null);
    try {
      const r = await fn();
      ok?.(r);
    } catch (e) {
      const m = String((e as Error).message ?? "");
      setMsg(
        m.startsWith("401") || m.startsWith("403")
          ? "Necesitás iniciar sesión como administrador."
          : `Error: ${m || "no se pudo completar la acción"}`
      );
    } finally {
      setBusy(null);
    }
  }

  const simularFecha = () =>
    run("fecha", () => send<SimFecha>("POST", `/sim/fecha?reentrenar=${reentrenar}`), (r) => {
      setFecha(r);
      setMundial(null);
    });

  const simularMundial = () =>
    run("mundial", () => send<SimMundial>("POST", `/sim/mundial?reentrenar=${reentrenar}`), (r) => {
      setMundial(r);
      setFecha(null);
    });

  const reentrenarModelo = () =>
    run("reentrenar", () => send<Reentrenamiento>("POST", "/ml/modelos/entrenar"), (r) => {
      setReentrenoManual(r);
      setMsg("Modelo reentrenado y activado.");
    });

  const crearEscenario = () =>
    run("crear", () => send<Escenario>("POST", "/sim/escenarios", { nombre, descripcion }), () => {
      setNombre("");
      setDescripcion("");
      cargarEscenarios();
    });

  const restaurar = (id: number) =>
    run(`restaurar-${id}`, () => send("POST", `/sim/escenarios/${id}/restaurar`), () => {
      setMsg("Escenario restaurado.");
      setFecha(null);
      setMundial(null);
    });

  const eliminar = (id: number) =>
    run(`eliminar-${id}`, () => send("DELETE", `/sim/escenarios/${id}`), cargarEscenarios);

  const reiniciar = () => {
    const base = escenarios[escenarios.length - 1]; // el más antiguo = punto de partida
    if (!base) {
      setMsg("Creá un escenario base antes de reiniciar la simulación.");
      return;
    }
    restaurar(base.id);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Panel de simulación</h2>
        <p className="text-sm text-muted">
          Proyectá el resto del Mundial con los modelos de IA, gestioná escenarios y reentrená el modelo.
        </p>
      </div>

      {!autenticado && (
        <div className="card border-bad/40 text-sm text-muted">
          Iniciá sesión como administrador para ejecutar simulaciones.
        </div>
      )}
      {msg && <div className="card text-sm text-accent">{msg}</div>}

      <div className="card space-y-4">
        <h3 className="text-sm font-semibold">Simulación</h3>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={reentrenar}
            onChange={(e) => setReentrenar(e.target.checked)}
          />
          Reentrenar el modelo al finalizar la simulación
        </label>
        <div className="flex flex-wrap gap-3">
          <button onClick={simularFecha} disabled={!!busy} className="btn">
            {busy === "fecha" ? "Simulando fecha…" : "Simular fecha"}
          </button>
          <button onClick={simularMundial} disabled={!!busy} className="btn">
            {busy === "mundial" ? "Simulando Mundial…" : "Simular Mundial"}
          </button>
          <button onClick={reentrenarModelo} disabled={!!busy} className="btn-ghost">
            {busy === "reentrenar" ? "Reentrenando…" : "Reentrenar modelo"}
          </button>
          <button onClick={reiniciar} disabled={!!busy} className="btn-ghost">
            Reiniciar simulación
          </button>
        </div>
        {reentrenoManual?.version != null && (
          <p className="text-xs text-muted">
            Modelo v{reentrenoManual.version} · accuracy {fmtPct(reentrenoManual.accuracy)} · brier{" "}
            {reentrenoManual.brier?.toFixed(3) ?? "—"}
          </p>
        )}
      </div>

      {fecha && <FechaResumen fecha={fecha} />}
      {mundial && <MundialResumen mundial={mundial} />}

      <div className="card space-y-4">
        <h3 className="text-sm font-semibold">Escenarios</h3>
        <p className="text-xs text-muted">
          Guardá el estado actual (partidos, posiciones, estadísticas, Elo y modelo) para comparar
          desenlaces o reiniciar la simulación.
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[180px]">
            <label className="text-xs text-muted">Nombre</label>
            <input
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Ej. Antes de octavos"
              className="mt-1 w-full rounded-lg border border-line bg-panel2 px-3 py-2 text-sm"
            />
          </div>
          <div className="flex-1 min-w-[180px]">
            <label className="text-xs text-muted">Descripción</label>
            <input
              value={descripcion}
              onChange={(e) => setDescripcion(e.target.value)}
              placeholder="Opcional"
              className="mt-1 w-full rounded-lg border border-line bg-panel2 px-3 py-2 text-sm"
            />
          </div>
          <button onClick={crearEscenario} disabled={!!busy} className="btn">
            Crear escenario
          </button>
        </div>

        <div className="divide-y divide-line">
          {escenarios.map((e) => (
            <div key={e.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <div>
                <div className="text-sm font-semibold">{e.nombre}</div>
                <div className="text-xs text-muted">
                  {e.descripcion} {e.creado_en ? `· ${new Date(e.creado_en).toLocaleString("es-AR")}` : ""}
                </div>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => restaurar(e.id)}
                  disabled={!!busy}
                  className="btn-ghost"
                >
                  {busy === `restaurar-${e.id}` ? "Restaurando…" : "Restaurar"}
                </button>
                <button
                  onClick={() => eliminar(e.id)}
                  disabled={!!busy}
                  className="navlink text-bad hover:text-bad"
                >
                  Eliminar
                </button>
              </div>
            </div>
          ))}
          {escenarios.length === 0 && (
            <p className="py-3 text-center text-sm text-muted">No hay escenarios guardados.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function FechaResumen({ fecha }: { fecha: SimFecha }) {
  return (
    <div className="card space-y-2">
      <h3 className="text-sm font-semibold">
        Fecha simulada {fecha.jornada ? `· ${fecha.jornada}` : ""}
      </h3>
      {fecha.mensaje && <p className="text-sm text-muted">{fecha.mensaje}</p>}
      <Partidos partidos={fecha.partidos} />
      <Reentreno r={fecha.reentrenado} />
    </div>
  );
}

function MundialResumen({ mundial }: { mundial: SimMundial }) {
  return (
    <div className="card space-y-3">
      <h3 className="text-sm font-semibold">Mundial simulado · {mundial.simulados} partidos</h3>
      {mundial.campeon && (
        <div className="grid gap-2 sm:grid-cols-3">
          <Podio label="🏆 Campeón" valor={mundial.campeon} accent />
          <Podio label="🥈 Subcampeón" valor={mundial.subcampeon} />
          <Podio label="🥉 Tercer puesto" valor={mundial.tercero} />
        </div>
      )}
      <details>
        <summary className="cursor-pointer text-sm text-accent">Ver partidos simulados</summary>
        <div className="mt-2">
          <Partidos partidos={mundial.partidos} />
        </div>
      </details>
      <Reentreno r={mundial.reentrenado} />
    </div>
  );
}

function Podio({ label, valor, accent }: { label: string; valor?: string; accent?: boolean }) {
  return (
    <div className="rounded-xl border border-line bg-panel2 p-3">
      <div className="text-xs text-muted">{label}</div>
      <div className={`text-lg font-bold ${accent ? "text-good" : ""}`}>{valor ?? "—"}</div>
    </div>
  );
}

function Partidos({ partidos }: { partidos: SimFecha["partidos"] }) {
  if (!partidos?.length) return null;
  return (
    <div className="max-h-72 overflow-y-auto text-sm">
      {partidos.map((p) => (
        <div key={p.partido_id} className="flex items-center justify-between border-t border-line py-1.5">
          <span className="truncate">
            {p.error ? (
              <span className="text-bad">#{p.partido_id}: {p.error}</span>
            ) : (
              <>
                {p.local} <b className="tabular-nums">{p.goles_local} - {p.goles_visitante}</b>{" "}
                {p.visitante}
              </>
            )}
          </span>
          {p.ronda && <span className="chip ml-2 shrink-0">{p.ronda.toLowerCase()}</span>}
        </div>
      ))}
    </div>
  );
}

function Reentreno({ r }: { r: Reentrenamiento }) {
  if (!r) return null;
  if (r.error) return <p className="text-xs text-bad">Reentrenamiento: {r.error}</p>;
  return (
    <p className="text-xs text-muted">
      Modelo reentrenado → v{r.version} · accuracy {fmtPct(r.accuracy)} · brier {r.brier?.toFixed(3) ?? "—"}
    </p>
  );
}

function fmtPct(v?: number) {
  return v == null ? "—" : `${Math.round(v * 1000) / 10}%`;
}
