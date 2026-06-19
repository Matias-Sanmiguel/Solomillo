"use client";
import { useEffect, useState } from "react";
import { get } from "@/lib/api";
import { Crest } from "../components/Crest";

type Fila = {
  equipo_id: number;
  nombre: string;
  escudo: string;
  pj: number;
  g: number;
  e: number;
  p: number;
  gf: number;
  gc: number;
  dg: number;
  pts: number;
  forma: string[];
};

type Clasificacion = Record<string, Fila[]>;

function FormaBadge({ r }: { r: string }) {
  const estilo =
    r === "W"
      ? "bg-good/20 text-good"
      : r === "L"
        ? "bg-bad/20 text-bad"
        : "bg-panel2 text-muted";
  return (
    <span
      className={`inline-flex h-5 w-5 items-center justify-center rounded text-[11px] font-bold ${estilo}`}
    >
      {r}
    </span>
  );
}

function GrupoTabla({ grupo, filas }: { grupo: string; filas: Fila[] }) {
  return (
    <div className="card overflow-x-auto">
      <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <span className="inline-block h-4 w-1 rounded bg-accent" />
        Grupo {grupo}
      </h3>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-[11px] uppercase tracking-wide text-muted">
            <th className="w-6 py-1 text-left font-medium">#</th>
            <th className="py-1 text-left font-medium">Equipo</th>
            <th className="py-1 text-center font-medium">PJ</th>
            <th className="py-1 text-center font-medium">G</th>
            <th className="py-1 text-center font-medium">E</th>
            <th className="py-1 text-center font-medium">P</th>
            <th className="py-1 text-center font-medium">GF</th>
            <th className="py-1 text-center font-medium">GC</th>
            <th className="py-1 text-center font-medium">DG</th>
            <th className="py-1 text-center font-medium">Pts</th>
            <th className="py-1 text-right font-medium">Forma</th>
          </tr>
        </thead>
        <tbody>
          {filas.map((f, i) => (
            <tr
              key={f.equipo_id}
              className={`border-t border-line ${i < 2 ? "bg-good/5" : ""}`}
            >
              <td className="py-2 text-muted tabular-nums">{i + 1}</td>
              <td className="py-2">
                <span className="flex items-center gap-2">
                  <Crest src={f.escudo} size={20} />
                  <span className="font-medium">{f.nombre}</span>
                </span>
              </td>
              <td className="py-2 text-center tabular-nums">{f.pj}</td>
              <td className="py-2 text-center tabular-nums">{f.g}</td>
              <td className="py-2 text-center tabular-nums">{f.e}</td>
              <td className="py-2 text-center tabular-nums">{f.p}</td>
              <td className="py-2 text-center tabular-nums">{f.gf}</td>
              <td className="py-2 text-center tabular-nums">{f.gc}</td>
              <td
                className={`py-2 text-center tabular-nums ${f.dg > 0 ? "text-good" : f.dg < 0 ? "text-bad" : "text-muted"}`}
              >
                {f.dg > 0 ? `+${f.dg}` : f.dg}
              </td>
              <td className="py-2 text-center font-bold tabular-nums text-accent">
                {f.pts}
              </td>
              <td className="py-2">
                <span className="flex justify-end gap-1">
                  {f.forma.map((r, k) => (
                    <FormaBadge key={k} r={r} />
                  ))}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function ClasificacionPage() {
  const [data, setData] = useState<Clasificacion>({});
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    get<Clasificacion>("/clasificacion")
      .then(setData)
      .catch(() => {})
      .finally(() => setCargando(false));
  }, []);

  const grupos = Object.keys(data).sort();

  if (cargando) return <p className="text-muted">Cargando clasificación…</p>;
  if (grupos.length === 0)
    return <p className="text-muted">No hay datos de clasificación.</p>;

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold">Clasificación</h2>
        <span className="chip">Mundial 2026</span>
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        {grupos.map((g) => (
          <GrupoTabla key={g} grupo={g} filas={data[g]} />
        ))}
      </div>
    </div>
  );
}
