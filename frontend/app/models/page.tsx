"use client";

import { useCallback, useEffect, useState } from "react";
import { get, send, getToken, Modelo, Metricas, Calibracion } from "@/lib/api";
import { AccuracyLine, CalibrationChart } from "../components/Charts";

export default function ModelsPage() {
  const [modelos, setModelos] = useState<Modelo[]>([]);
  const [metricas, setMetricas] = useState<Metricas | null>(null);
  const [cal, setCal] = useState<Calibracion | null>(null);
  const [entrenando, setEntrenando] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const cargar = useCallback(() => {
    get<Modelo[]>("/ml/modelos")
      .then((ms) => {
        setModelos(ms);
        const activo = ms.find((m) => m.activo && m.tipo === "clasificacion") ?? ms[0];
        if (activo) {
          get<Metricas>(`/ml/modelos/${activo.version}/metricas`).then(setMetricas).catch(() => {});
          get<Calibracion>(`/ml/calibracion/${activo.version}`).then(setCal).catch(() => {});
        }
      })
      .catch(() => {});
  }, []);

  useEffect(cargar, [cargar]);

  async function reentrenar() {
    setEntrenando(true);
    setMsg(null);
    try {
      const m = await send<Modelo>("POST", "/ml/modelos/entrenar");
      setMsg(`Modelo v${m.version} entrenado y activado (accuracy ${fmtPct(m.accuracy)}).`);
      cargar();
    } catch (e) {
      const t = String((e as Error).message ?? "");
      setMsg(
        t.startsWith("401") || t.startsWith("403")
          ? "Necesitás iniciar sesión como administrador para reentrenar."
          : t.startsWith("409")
          ? "Datos insuficientes para reentrenar (mínimo 20 partidos finalizados)."
          : "No se pudo reentrenar el modelo."
      );
    } finally {
      setEntrenando(false);
    }
  }

  const clasif = modelos.filter((m) => m.tipo === "clasificacion");

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold">Modelos de predicción</h2>
        {getToken() && (
          <button onClick={reentrenar} disabled={entrenando} className="btn">
            {entrenando ? "Reentrenando…" : "Reentrenar modelo"}
          </button>
        )}
      </div>
      {msg && <div className="card text-sm text-accent">{msg}</div>}

      <div className="card overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-xs uppercase text-muted">
            <tr>
              <th className="py-2">Versión</th>
              <th>Tipo</th>
              <th className="text-right">Accuracy</th>
              <th className="text-right">Log-loss</th>
              <th className="text-right">Brier</th>
              <th className="text-right">Estado</th>
            </tr>
          </thead>
          <tbody>
            {modelos.map((m) => (
              <tr key={`${m.tipo}-${m.version}`} className="border-t border-line">
                <td className="py-2 font-semibold">v{m.version}</td>
                <td className="text-muted">{m.tipo}</td>
                <td className="text-right tabular-nums">{fmtPct(m.accuracy)}</td>
                <td className="text-right tabular-nums">{m.log_loss ? m.log_loss.toFixed(3) : "—"}</td>
                <td className="text-right tabular-nums">{m.brier ? m.brier.toFixed(3) : "—"}</td>
                <td className="text-right">
                  {m.activo ? (
                    <span className="chip border-good/40 text-good">activo</span>
                  ) : (
                    <span className="chip">histórico</span>
                  )}
                </td>
              </tr>
            ))}
            {modelos.length === 0 && (
              <tr>
                <td colSpan={6} className="py-4 text-center text-muted">
                  No hay modelos. Entrená con POST /ml/modelos/entrenar.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {metricas && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Metric label="Predicciones evaluadas" value={String(metricas.n)} />
          <Metric label="Accuracy" value={fmtPct(metricas.accuracy ?? 0)} accent />
          <Metric
            label="Log-loss"
            value={metricas.log_loss != null ? metricas.log_loss.toFixed(3) : "—"}
          />
        </div>
      )}

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card">
          <h3 className="mb-1 text-sm font-semibold">Accuracy acumulada</h3>
          <p className="mb-3 text-xs text-muted">
            Precisión del modelo a medida que se resuelven partidos {clasif[0] && `(v${activeVersion(clasif)})`}.
          </p>
          <AccuracyLine serie={metricas?.serie} />
        </div>
        <div className="card">
          <h3 className="mb-1 text-sm font-semibold">Calibración</h3>
          <p className="mb-3 text-xs text-muted">
            Confianza declarada vs. precisión real. La diagonal es la calibración perfecta.
          </p>
          <CalibrationChart cal={cal} />
        </div>
      </div>
    </div>
  );
}

function activeVersion(ms: Modelo[]) {
  return (ms.find((m) => m.activo) ?? ms[0])?.version;
}

function Metric({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="card">
      <div className="text-xs text-muted">{label}</div>
      <div className={`stat ${accent ? "text-good" : ""}`}>{value}</div>
    </div>
  );
}

function fmtPct(v: number) {
  return `${Math.round(v * 1000) / 10}%`;
}
