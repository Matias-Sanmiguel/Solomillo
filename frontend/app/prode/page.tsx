"use client";

import { useEffect, useMemo, useState } from "react";
import {
  get,
  send,
  login,
  getToken,
  setToken,
  Equipo,
  ProdePartido,
  Signo,
  fmtFecha,
} from "@/lib/api";
import { Crest } from "../components/Crest";

const SIGNOS: { v: Signo; label: string }[] = [
  { v: "LOCAL", label: "1" },
  { v: "EMPATE", label: "X" },
  { v: "VISITANTE", label: "2" },
];

export default function ProdePage() {
  const [logueado, setLogueado] = useState(false);
  const [equipos, setEquipos] = useState<Equipo[]>([]);
  const [partidos, setPartidos] = useState<ProdePartido[]>([]);
  const [cargando, setCargando] = useState(true);

  const cargar = () => {
    Promise.all([get<Equipo[]>("/equipos"), get<ProdePartido[]>("/prode/partidos")])
      .then(([e, p]) => {
        setEquipos(e);
        setPartidos(p);
      })
      .catch(() => {})
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    setLogueado(!!getToken());
    cargar();
  }, []);

  const eq = useMemo(() => {
    const m = new Map<number, Equipo>();
    equipos.forEach((e) => m.set(e.id, e));
    return m;
  }, [equipos]);

  if (!logueado)
    return (
      <LoginForm
        onOk={() => {
          setLogueado(true);
          cargar();
        }}
      />
    );

  if (cargando) return <p className="text-muted">Cargando partidos…</p>;

  return (
    <div>
      <div className="mb-5 flex items-baseline justify-between">
        <h2 className="text-lg font-semibold">Pronosticá los próximos partidos</h2>
        <button
          className="navlink"
          onClick={() => {
            setToken(null);
            setLogueado(false);
          }}
        >
          Salir
        </button>
      </div>

      {partidos.length === 0 && (
        <p className="text-muted">No hay partidos abiertos para pronosticar.</p>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        {partidos.map((p) => (
          <PronosticoCard
            key={p.partido_id}
            partido={p}
            local={eq.get(p.local_id)}
            visit={eq.get(p.visitante_id)}
            onGuardado={cargar}
          />
        ))}
      </div>
    </div>
  );
}

function PronosticoCard({
  partido,
  local,
  visit,
  onGuardado,
}: {
  partido: ProdePartido;
  local?: Equipo;
  visit?: Equipo;
  onGuardado: () => void;
}) {
  const mp = partido.mi_pronostico;
  const [signo, setSigno] = useState<Signo | null>(mp?.signo ?? null);
  const [gl, setGl] = useState<string>(mp?.goles_local?.toString() ?? "");
  const [gv, setGv] = useState<string>(mp?.goles_visitante?.toString() ?? "");
  const [estado, setEstado] = useState<"idle" | "guardando" | "ok" | "error">("idle");

  const guardar = async () => {
    if (!signo) return;
    setEstado("guardando");
    try {
      await send("PUT", `/prode/pronosticos/${partido.partido_id}`, {
        signo,
        golesLocal: gl === "" ? null : Number(gl),
        golesVisitante: gv === "" ? null : Number(gv),
      });
      setEstado("ok");
      onGuardado();
    } catch {
      setEstado("error");
    }
  };

  return (
    <div className="card">
      <div className="mb-3 text-xs text-muted">{fmtFecha(partido.fecha_hora)}</div>

      <div className="mb-4 flex items-center justify-between gap-2">
        <Team nombre={local?.nombre ?? `#${partido.local_id}`} escudo={local?.escudo} />
        <span className="text-xs font-medium text-muted">vs</span>
        <Team nombre={visit?.nombre ?? `#${partido.visitante_id}`} escudo={visit?.escudo} right />
      </div>

      <div className="mb-3 flex gap-1">
        {SIGNOS.map((s) => (
          <button
            key={s.v}
            onClick={() => setSigno(s.v)}
            className={`navlink flex-1 ${signo === s.v ? "navlink-active" : ""}`}
          >
            {s.label}
          </button>
        ))}
      </div>

      <div className="mb-3 flex items-center justify-center gap-2 text-sm">
        <input
          inputMode="numeric"
          value={gl}
          onChange={(e) => setGl(e.target.value.replace(/\D/g, ""))}
          placeholder="–"
          className="w-12 rounded-lg border border-line bg-panel2 p-2 text-center"
        />
        <span className="text-muted">:</span>
        <input
          inputMode="numeric"
          value={gv}
          onChange={(e) => setGv(e.target.value.replace(/\D/g, ""))}
          placeholder="–"
          className="w-12 rounded-lg border border-line bg-panel2 p-2 text-center"
        />
        <span className="ml-2 text-xs text-muted">resultado exacto (opcional)</span>
      </div>

      <button
        onClick={guardar}
        disabled={!signo || estado === "guardando"}
        className="navlink navlink-active w-full disabled:opacity-50"
      >
        {estado === "guardando"
          ? "Guardando…"
          : estado === "ok"
          ? "Guardado ✓"
          : mp
          ? "Actualizar pronóstico"
          : "Guardar pronóstico"}
      </button>
      {estado === "error" && (
        <p className="mt-2 text-center text-xs text-red-400">No se pudo guardar.</p>
      )}
    </div>
  );
}

function LoginForm({ onOk }: { onOk: () => void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(false);

  const entrar = async () => {
    setError(false);
    try {
      await login(email, password);
      onOk();
    } catch {
      setError(true);
    }
  };

  return (
    <div className="card max-w-sm">
      <h2 className="mb-2 text-lg font-semibold">Ingresá para jugar al prode</h2>
      <p className="mb-4 text-sm text-muted">Usá tu cuenta de Solomillo.</p>
      <div className="flex flex-col gap-2">
        <input
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="email"
          className="rounded-lg border border-line bg-panel2 p-2 text-sm"
        />
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="contraseña"
          className="rounded-lg border border-line bg-panel2 p-2 text-sm"
        />
        <button onClick={entrar} className="navlink navlink-active mt-1">
          Entrar
        </button>
        {error && <p className="text-center text-xs text-red-400">Credenciales inválidas.</p>}
      </div>
    </div>
  );
}

function Team({ nombre, escudo, right }: { nombre: string; escudo?: string; right?: boolean }) {
  return (
    <div className={`flex flex-1 items-center gap-2 ${right ? "flex-row-reverse text-right" : ""}`}>
      <Crest src={escudo} size={28} />
      <span className="truncate text-sm font-semibold">{nombre}</span>
    </div>
  );
}
